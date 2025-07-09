package de.elia.cameraplugin.cameraPlugin;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import de.elia.cameraplugin.cameraPlugin.CamCommand;
import de.elia.cameraplugin.cameraPlugin.CamTabCompleter;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Projectile;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Collections;
import java.util.Collection;
import java.util.ArrayList;

import static org.bukkit.Sound.ENTITY_ITEM_BREAK;

@SuppressWarnings("removal")
public final class CameraPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, CameraData> cameraPlayers = new HashMap<>();
    private final Map<UUID, Long> distanceMessageCooldown = new HashMap<>();
    private final Set<UUID> damageImmunityBypass = new HashSet<>();
    private final Map<UUID, UUID> armorStandOwners = new HashMap<>();
    private final Map<UUID, UUID> hitboxEntities = new HashMap<>();
    private final Set<UUID> pendingDamage = new HashSet<>();

    private static final String NO_COLLISION_TEAM = "cam_no_push";

    // Configurable values
    private boolean maxDistanceEnabled;
    private double maxDistance;
    private int distanceWarningCooldown;
    private double drowningDamage;
    private boolean armorStandNameVisible;
    private boolean armorStandVisible;
    private boolean armorStandGravity;
    private double armorStandDamageAmount;
    private boolean armorDurabilityLoss;
    private boolean damageIgnoreArmor;
    private VisibilityMode playerVisibilityMode;
    private boolean allowInvisibilityPotion;
    private Object Sound;

    private enum VisibilityMode { CAM, ALL, NONE }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        setupNoCollisionTeam();
        this.getCommand("cam").setExecutor(new CamCommand(this));
        this.getCommand("cam").setTabCompleter(new CamTabCompleter());
        this.getServer().getPluginManager().registerEvents(this, this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            updateViewerTeam(online);
        }
        getLogger().info("CameraPlugin wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        // Erstellt eine Kopie der Keys, um ConcurrentModificationException zu vermeiden
        for (UUID playerId : new HashSet<>(cameraPlayers.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                exitCameraMode(player);
            }
        }
        getLogger().info("CameraPlugin wurde deaktiviert!");
    }


    public void enterCameraMode(Player player) {
        // *** Inventar und Rüstung speichern ***
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalInventory = playerInventory.getContents();
        ItemStack[] originalArmor = playerInventory.getArmorContents();
        Collection<PotionEffect> pausedEffects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            pausedEffects.add(effect);
            player.removePotionEffect(effect.getType());
        }
        // Create clones for the armor stand so the player's original items
        // can be restored later while durability changes are preserved.
        ItemStack[] armorStandArmor = new ItemStack[originalArmor.length];
        for (int i = 0; i < originalArmor.length; i++) {
            if (originalArmor[i] != null) {
                armorStandArmor[i] = originalArmor[i].clone();
            }
        }
        boolean originalSilent = player.isSilent();

        // *** Inventar und Rüstung leeren ***
        playerInventory.clear();
        playerInventory.setArmorContents(new ItemStack[4]);// Leeres Array für Rüstungsslots
        player.updateInventory();

        Location playerLocation = player.getLocation();

        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(playerLocation, EntityType.ARMOR_STAND);
        armorStand.setVisible(armorStandVisible);
        armorStand.setGravity(armorStandGravity);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomName(getMessage("armorstand.name-format").replace("{player}", player.getName()));
        armorStand.setCustomNameVisible(armorStandNameVisible);
        armorStand.setInvulnerable(false);
        armorStand.setMarker(false);
        armorStand.setMaxHealth(20.0);
        armorStand.setHealth(20.0);
        armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.CHEST, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.LEGS, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.FEET, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.OFF_HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            playerHead.setItemMeta(skullMeta);
        }
        armorStand.getEquipment().setHelmet(playerHead);

        // Equip the armor stand with the player's armor pieces so durability and
        // enchantments are retained while the player is in camera mode. The
        // original armor items are stored in CameraData and will be returned to
        // the player on exit. Using the same ItemStack objects ensures that any
        // durability loss while the armor stand is damaged is preserved.
        armorStand.getEquipment().setArmorContents(originalArmor);

        Villager hitbox = (Villager) player.getWorld().spawnEntity(playerLocation, EntityType.VILLAGER);
        hitbox.setInvisible(true);
        hitbox.setSilent(true);
        hitbox.setAI(false);
        hitbox.setInvulnerable(false);
        hitbox.setCustomName(getMessage("hitbox.name-format").replace("{player}", player.getName()));
        hitbox.setCustomNameVisible(false);
        hitbox.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        hitbox.setProfession(Villager.Profession.NONE);
        hitbox.setVillagerType(Villager.Type.PLAINS);
        hitbox.setVillagerLevel(1);
        hitbox.setCanPickupItems(false);
        hitbox.teleport(armorStand.getLocation().add(0, 0.1, 0));

        GameMode originalGameMode = player.getGameMode();
        boolean originalAllowFlight = player.getAllowFlight();
        boolean originalFlying = player.isFlying();

        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setSilent(true);
        if (allowInvisibilityPotion) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true); // ensure flight remains enabled
                player.setFlying(true);       // keep player flying
                if (allowInvisibilityPotion && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                }
            }
        }.runTaskLater(this, 1L);

        double reaggroRadius = 64.0;
        for (Entity entity : player.getNearbyEntities(reaggroRadius, reaggroRadius, reaggroRadius)) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                if (player.equals(mob.getTarget())) {
                    mob.setTarget(hitbox); // redirect aggro to hitbox
                }
            }
        }

        // *** Gespeichertes Inventar an CameraData übergeben ***
        cameraPlayers.put(player.getUniqueId(), new CameraData(armorStand, hitbox, originalGameMode, originalAllowFlight, originalFlying, originalSilent, originalInventory, originalArmor, pausedEffects));
        armorStandOwners.put(armorStand.getUniqueId(), player.getUniqueId());
        hitboxEntities.put(hitbox.getUniqueId(), player.getUniqueId());

        startHitboxSync(armorStand, hitbox);
        addPlayerToNoCollisionTeam(player);
        updateViewerTeam(player);
        updateVisibilityForAll();
    }

    public void exitCameraMode(Player player) {
        CameraData cameraData = cameraPlayers.get(player.getUniqueId());
        if (cameraData == null) return;

        ArmorStand armorStand = cameraData.getArmorStand();
        Villager hitbox = cameraData.getHitbox();

        double reaggroRadius = 64.0;
        for (Entity entity : armorStand.getNearbyEntities(reaggroRadius, reaggroRadius, reaggroRadius)) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                if (armorStand.equals(mob.getTarget()) || hitbox.equals(mob.getTarget())) {
                    mob.setTarget(player);
                }
            }
        }

        // Zuerst zum Körper teleportieren
        player.teleport(armorStand.getLocation());
        // Synchronise durability from the armor stand back to the player's items
        syncArmorBack(player, armorStand, cameraData.getOriginalArmorContents());


        // *** Inventar und Rüstung wiederherstellen ***
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear(); // Sicherheitshalber leeren, falls Items hinzugefügt wurden
        playerInventory.setContents(cameraData.getOriginalInventoryContents());
        playerInventory.setArmorContents(cameraData.getOriginalArmorContents());
        player.updateInventory();

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.setFireTicks(0);
        for (PotionEffect effect : cameraData.getPausedEffects()) {
            player.addPotionEffect(effect);
        }
        player.setGameMode(cameraData.getOriginalGameMode());
        player.setAllowFlight(cameraData.getOriginalAllowFlight());
        player.setFlying(cameraData.getOriginalFlying());
        player.setSilent(cameraData.getOriginalSilent());

        removePlayerFromNoCollisionTeam(player);

        cameraPlayers.remove(player.getUniqueId());
        updateViewerTeam(player);

        // Aufräumen
        armorStandOwners.remove(armorStand.getUniqueId());
        hitboxEntities.remove(hitbox.getUniqueId());

        // Clear equipment before removing to avoid item drops or duplication
        armorStand.getEquipment().setArmorContents(new ItemStack[4]);
        armorStand.getEquipment().setHelmet(new ItemStack(Material.AIR));
        armorStand.remove();
        hitbox.remove();

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(this, player);
        }
        updateVisibilityForAll();
    }

    public boolean isInCameraMode(Player player) {
        return cameraPlayers.containsKey(player.getUniqueId());
    }

    private void startArmorStandHealthCheck(Player player, ArmorStand armorStand) {
        final Location initialLocation = armorStand.getLocation().clone();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cameraPlayers.containsKey(player.getUniqueId()) || !player.isOnline() || armorStand.isDead()) {
                    this.cancel();
                    return;
                }
                if (!armorStand.getLocation().getWorld().equals(initialLocation.getWorld()) ||
                        armorStand.getLocation().distanceSquared(initialLocation) > 0.01) {
                    player.sendMessage(getMessage("body-moved"));
                    exitCameraMode(player);
                    this.cancel();
                    return;
                }
                if (armorStand.getEyeLocation().getBlock().getType().isSolid()) {
                    player.sendMessage(getMessage("body-suffocating"));
                    exitCameraMode(player);
                    this.cancel();
                }
                if (armorStand.getRemainingAir() < armorStand.getMaximumAir() && armorStand.getRemainingAir() <= 0) {
                    if (armorStand.getTicksLived() % 20 == 0) {
                        player.sendMessage(getMessage("body-drowning"));

                        exitCameraMode(player);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline() && !player.isDead()) {
                                    applyDamageWithArmor(player, drowningDamage);
                                }
                            }
                        }.runTaskLater(CameraPlugin.this, 1L);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 1L);
    }

    private void startHitboxSync(ArmorStand armorStand, Villager hitbox) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (armorStand.isDead() || hitbox.isDead()) {
                    this.cancel();
                    return;
                }
                hitbox.teleport(armorStand.getLocation().add(0, 0.1, 0));
            }
        }.runTaskTimer(this, 1L, 1L);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandDamage(EntityDamageEvent event) {
        Entity damagedEntity = event.getEntity();
        UUID ownerUUID = null;

        if (damagedEntity instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(damagedEntity.getUniqueId());
        } else if (damagedEntity instanceof Villager) {
            ownerUUID = hitboxEntities.get(damagedEntity.getUniqueId());
        }

        if (ownerUUID == null) {
            return;
        }

        // Ignore damage to managed armor stands and hitboxes
        event.setCancelled(true);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        if (entity instanceof Villager) {
            UUID ownerUUID = hitboxEntities.get(entity.getUniqueId());
            if (ownerUUID != null) {
                event.setCancelled(true);
                if (player.getUniqueId().equals(ownerUUID)) {
                    player.sendMessage(getMessage("camera-off"));
                    Player owner = Bukkit.getPlayer(ownerUUID);
                    if (owner != null) {
                        exitCameraMode(owner);
                    }
                } else {
                    player.sendMessage(getMessage("cant-interact-other"));
                }
            }
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        if (cameraPlayers.containsKey(player.getUniqueId())) {
            CameraData data = cameraPlayers.get(player.getUniqueId());
            if (data != null && data.getHitbox() != null && !data.getHitbox().isDead()) {
                event.setTarget(data.getHitbox());
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player shooter = (Player) event.getEntity().getShooter();

        if (cameraPlayers.containsKey(shooter.getUniqueId())) {
            event.setCancelled(true); // Generell Projektile verhindern
            shooter.sendMessage(getMessage("no-projectiles"));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.stopSound(SoundCategory.PLAYERS);
        }
        if (!cameraPlayers.containsKey(player.getUniqueId())) {
            return;
        }

        Action action = event.getAction();

        // Verhindert jegliche Interaktionen im Kamera-Modus
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK ||
                action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK ||
                action == Action.PHYSICAL) {
            event.setCancelled(true);
            player.stopSound(SoundCategory.PLAYERS);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player &&
                cameraPlayers.containsKey(event.getEntity().getUniqueId()) &&
                !damageImmunityBypass.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (cameraPlayers.containsKey(event.getPlayer().getUniqueId())) {
            exitCameraMode(event.getPlayer());
        }
        distanceMessageCooldown.remove(event.getPlayer().getUniqueId());
        removePlayerFromNoCollisionTeam(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateViewerTeam(event.getPlayer());
                updateVisibilityForAll();
            }
        }.runTaskLater(this, 1L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!cameraPlayers.containsKey(player.getUniqueId())) return;
        Location to = event.getTo();
        if (to == null) return;

        Block blockAt = to.getBlock();
        if (blockAt.getType() == Material.LAVA) {
            player.sendMessage(getMessage("cant-fly-in-lava"));
            exitCameraMode(player);
            return;
        }

        Location standLoc = cameraPlayers.get(player.getUniqueId()).getArmorStand().getLocation();
        // Always prevent players from switching worlds, optionally limit distance
        if (!to.getWorld().equals(standLoc.getWorld()) ||
                (maxDistanceEnabled && to.distanceSquared(standLoc) > maxDistance * maxDistance)) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (distanceMessageCooldown.getOrDefault(player.getUniqueId(), 0L) < now) {
                player.sendMessage(getMessage("distance-limit").replace("{distance}", String.valueOf(maxDistance)));
                distanceMessageCooldown.put(player.getUniqueId(), now + TimeUnit.SECONDS.toMillis(distanceWarningCooldown));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void suppressAdventureMoveSound(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.stopSound(SoundCategory.PLAYERS);
            player.stopSound(SoundCategory.BLOCKS);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Der Spieler soll sterben, aber vorher den Kamera-Modus korrekt beenden.
        // Die Drops und XP werden vom Tod selbst gehandhabt.
        if (cameraPlayers.containsKey(event.getEntity().getUniqueId())) {
            // Wichtig: Die Items sind im CameraData gespeichert.
            // Wir müssen die Drops des Todes-Events leeren und unsere eigenen Items fallen lassen.
            Player player = event.getEntity();
            CameraData data = cameraPlayers.get(player.getUniqueId());

            event.getDrops().clear(); // Leert die Standard-Drops (leeres Inventar)

            // Füge die gespeicherten Items zu den Drops hinzu
            for (ItemStack item : data.getOriginalInventoryContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getDrops().add(item);
                }
            }
            for (ItemStack item : data.getOriginalArmorContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getDrops().add(item);
                }
            }

            exitCameraMode(player);
        }
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player &&
                cameraPlayers.containsKey(event.getEntered().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player &&
                cameraPlayers.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (cameraPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!allowInvisibilityPotion) return;
        if (playerVisibilityMode == VisibilityMode.NONE) return;
        if (cameraPlayers.containsKey(player.getUniqueId())) return;
        if (!PotionEffectType.INVISIBILITY.equals(event.getModifiedType())) return;

        Bukkit.getScheduler().runTask(this, () -> updateViewerTeam(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void filterCommandSuggestions(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(cmd -> cmd.equalsIgnoreCase("camplugin:cam"));
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!cameraPlayers.containsKey(attacker.getUniqueId())) {
            return;
        }

        Entity target = event.getEntity();
        UUID ownerUUID = null;

        if (target instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(target.getUniqueId());
        } else if (target instanceof Villager) {
            ownerUUID = hitboxEntities.get(target.getUniqueId());
        }

        // Cancel attacks on anything except the player's own armor stand or hitbox
        if (ownerUUID == null || !ownerUUID.equals(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void suppressAdventureHitSound(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker &&
                attacker.getGameMode() == GameMode.ADVENTURE) {
            attacker.stopSound(SoundCategory.PLAYERS);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player &&
                cameraPlayers.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void applyDamageWithArmor(Player player, double damage) {
        if (damage <= 0 || player.isDead()) return;
        damageImmunityBypass.add(player.getUniqueId());
        try {
            player.damage(damage);
        } finally {
            // Führe das Entfernen mit einer kleinen Verzögerung aus, um sicherzustellen, dass der Schaden verarbeitet wird
            new BukkitRunnable() {
                @Override
                public void run() {
                    damageImmunityBypass.remove(player.getUniqueId());
                }
            }.runTaskLater(this, 1L);
        }
    }



    // Remaining blocks: 1 damage each
    double fallDamage = 0.0;

                if (fallBlocks >= 1) {
        // First 3 blocks (or less if fall is shorter)
        double firstBlocks = Math.min(fallBlocks, 3);
        fallDamage += firstBlocks * 4.0;

        if (fallBlocks > 3) {
            // Next 5 blocks (blocks 4-8)
            double middleBlocks = Math.min(fallBlocks - 3, 5);
            fallDamage += middleBlocks * 2.0;

            if (fallBlocks > 8) {
                // Remaining blocks (9+)
                double remainingBlocks = fallBlocks - 8;
                fallDamage += remainingBlocks * 1.0;
            }
        }
    }

    // Apply Density enchantment (adds 0.5 damage per level per block)
    int density = weapon.getEnchantmentLevel(Enchantment.DENSITY);
                if (density > 0) {
        fallDamage += fallBlocks * density * 0.5;
    }

    // Critical hit increases fall damage by 50%
                if (airborne) {
        fallDamage *= 1.5;
    }

    base += fallDamage;
}

// Apply Breach enchantment (adds 1 damage per level)
int breach = weapon.getEnchantmentLevel(Enchantment.BREACH);
            if (breach > 0) {
base += breach;
            }

correctBase = base;
        }

                return Math.max(0.0, damage + (correctBase - eventBase));
        }

/**
 * Wendet Haltbarkeitsschaden auf die Rüstung an und berücksichtigt dabei
 * die Unbreaking-Verzauberung.
 */
private void applyArmorDurabilityLoss(ArmorStand stand) {
    for (ItemStack item : stand.getEquipment().getArmorContents()) {
        if (item == null) continue;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) continue;

        // Since Spigot 1.21 the unbreaking enchantment constant was renamed
        // from DURABILITY to UNBREAKING. Use the new name so it resolves
        // correctly when compiling against the latest API.
        int unbreaking = item.getEnchantmentLevel(Enchantment.UNBREAKING);
        double chanceNoDamage = switch (unbreaking) {
            case 0 -> 0.0;
            case 1 -> 0.5;
            case 2 -> 2.0 / 3.0;
            default -> 0.75; // Level 3 or higher
        };

        if (Math.random() >= chanceNoDamage) {
            damageable.setDamage(damageable.getDamage() + 1);
            item.setItemMeta(damageable);
        }
    }
}

/**
 * Copy durability values from the armor stand's equipment back to the
 * player's original armor items.
 */
private void syncArmorBack(Player player, ArmorStand armorStand, ItemStack[] originalArmor) {
    ItemStack[] standArmor = armorStand.getEquipment().getArmorContents();
    for (int i = 0; i < originalArmor.length; i++) {
        ItemStack original = originalArmor[i];
        ItemStack fromStand = standArmor.length > i ? standArmor[i] : null;

        if (original != null && fromStand != null && original.getType() == fromStand.getType()) {
            ItemMeta standMeta = fromStand.getItemMeta();
            ItemMeta originalMeta = original.getItemMeta();
            if (standMeta instanceof Damageable && originalMeta instanceof Damageable) {
                int damage = ((Damageable) standMeta).getDamage();
                ((Damageable) originalMeta).setDamage(damage);
                original.setItemMeta(originalMeta);
            }
        }
    }
    // Sicherheitshalber noch einmal setzen
    player.getInventory().setArmorContents(originalArmor);
}


public String getMessage(String path) {
    return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path, ""));
}

private void loadConfigValues() {
    maxDistanceEnabled = getConfig().getBoolean("camera-mode.max-distance-enabled", true);
    maxDistance = getConfig().getDouble("camera-mode.max-distance", 100.0);
    distanceWarningCooldown = getConfig().getInt("camera-mode.distance-warning-cooldown", 3);
    drowningDamage = getConfig().getDouble("camera-mode.drowning-damage", 2.0);
    String visibility = getConfig().getString("camera-mode.player_visibility_mode", "cam").toLowerCase();
    playerVisibilityMode = switch (visibility) {
        case "true" -> VisibilityMode.ALL;
        case "false" -> VisibilityMode.NONE;
        default -> VisibilityMode.CAM;
    };
    allowInvisibilityPotion = getConfig().getBoolean("camera-mode.allow_invisibility_potion", true);
    armorStandNameVisible = getConfig().getBoolean("armorstand.name-visible", true);
    armorStandVisible = getConfig().getBoolean("armorstand.visible", true);
    armorStandGravity = getConfig().getBoolean("armorstand.gravity", true);
    armorStandDamageAmount = getConfig().getDouble("armorstand.damage-amount", 1.0);
    armorDurabilityLoss = getConfig().getBoolean("armorstand.durability-loss", true);
    damageIgnoreArmor = getConfig().getBoolean("armorstand.damage-ignore-armor", true);
}

private void setupNoCollisionTeam() {
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
    if (team == null) {
        team = scoreboard.registerNewTeam(NO_COLLISION_TEAM);
    }
    team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    team.setCanSeeFriendlyInvisibles(true);
}

private void addPlayerToNoCollisionTeam(Player player) {
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
    if (team != null) {
        team.addEntry(player.getName());
    }
}

private void removePlayerFromNoCollisionTeam(Player player) {
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
    if (team != null) {
        team.removeEntry(player.getName());
    }
}

private void updateViewerTeam(Player player) {
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
    if (team == null) return;

    boolean inCam = cameraPlayers.containsKey(player.getUniqueId());
    boolean shouldBeMember;

    if (inCam) {
        shouldBeMember = true;
    } else if (allowInvisibilityPotion && playerVisibilityMode == VisibilityMode.ALL) {
        shouldBeMember = !player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    } else {
        shouldBeMember = false;
    }

    if (shouldBeMember) {
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    } else {
        if (team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }
}

private void applyVisibility(Player camPlayer, Player viewer) {
    if (camPlayer.equals(viewer)) return;
    switch (playerVisibilityMode) {
        case CAM -> {
            if (cameraPlayers.containsKey(viewer.getUniqueId())) {
                viewer.showPlayer(this, camPlayer);
            } else {
                viewer.hidePlayer(this, camPlayer);
            }
        }
        case ALL -> viewer.showPlayer(this, camPlayer);
        case NONE -> viewer.hidePlayer(this, camPlayer);
    }
}

private void updateVisibilityForAll() {
    for (UUID camId : cameraPlayers.keySet()) {
        Player cam = Bukkit.getPlayer(camId);
        if (cam == null) continue;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyVisibility(cam, viewer);
        }
    }
}

public void reloadPlugin(Player initiator) {
    for (UUID uuid : new HashSet<>(cameraPlayers.keySet())) {
        Player camPlayer = Bukkit.getPlayer(uuid);
        if (camPlayer != null) {
            camPlayer.sendMessage(getMessage("reload-exit"));
            exitCameraMode(camPlayer);
        }
    }
    reloadConfig();
    loadConfigValues();
    setupNoCollisionTeam();
    for (Player online : Bukkit.getOnlinePlayers()) {
        updateViewerTeam(online);
    }
}

// *** CameraData Klasse erweitert ***
private static class CameraData {
    private final ArmorStand armorStand;
    private final Villager hitbox;
    private final GameMode originalGameMode;
    private final boolean originalAllowFlight;
    private final boolean originalFlying;
    private final boolean originalSilent;
    private final ItemStack[] originalInventoryContents; // Für Inventar
    private final ItemStack[] originalArmorContents;     // Für Rüstung
    private final Collection<PotionEffect> pausedEffects;

    public CameraData(ArmorStand armorStand, Villager hitbox, GameMode originalGameMode, boolean originalAllowFlight, boolean originalFlying, boolean originalSilent, ItemStack[] originalInventoryContents, ItemStack[] originalArmorContents, Collection<PotionEffect> pausedEffects) {
        this.armorStand = armorStand;
        this.hitbox = hitbox;
        this.originalGameMode = originalGameMode;
        this.originalAllowFlight = originalAllowFlight;
        this.originalFlying = originalFlying;
        this.originalSilent = originalSilent;
        this.originalInventoryContents = originalInventoryContents;
        this.originalArmorContents = originalArmorContents;
        this.pausedEffects = pausedEffects;
    }

    public ArmorStand getArmorStand() { return armorStand; }
    public Villager getHitbox() { return hitbox; }
    public GameMode getOriginalGameMode() { return originalGameMode; }
    public boolean getOriginalAllowFlight() { return originalAllowFlight; }
    public boolean getOriginalFlying() { return originalFlying; }
    public boolean getOriginalSilent() { return originalSilent; }
    public ItemStack[] getOriginalInventoryContents() { return originalInventoryContents; }
    public ItemStack[] getOriginalArmorContents() { return originalArmorContents; }