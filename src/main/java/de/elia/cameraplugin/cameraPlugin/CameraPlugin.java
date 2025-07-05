package de.elia.cameraplugin.cameraPlugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class CameraPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, CameraData> cameraPlayers = new HashMap<>();
    private final Map<UUID, Long> distanceMessageCooldown = new HashMap<>();
    private final Set<UUID> damageImmunityBypass = new HashSet<>();
    private final Map<UUID, UUID> armorStandOwners = new HashMap<>();
    private final Map<UUID, UUID> hitboxEntities = new HashMap<>();

    private static final String NO_COLLISION_TEAM = "cam_no_push";

    // Configurable values
    private boolean maxDistanceEnabled;
    private double maxDistance;
    private int distanceWarningCooldown;
    private double drowningDamage;
    private boolean armorStandNameVisible;
    private boolean armorStandVisible;
    private boolean armorStandGravity;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        setupNoCollisionTeam();
        this.getServer().getPluginManager().registerEvents(this, this);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("no-player"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("camplugin.use")) {
            player.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("cam")) {
            if (cameraPlayers.containsKey(player.getUniqueId())) {
                exitCameraMode(player);
                player.sendMessage(getMessage("camera-off"));
            } else {
                enterCameraMode(player);
                player.sendMessage(getMessage("camera-on"));
            }
            return true;
        }
        return false;
    }

    private void enterCameraMode(Player player) {
        // *** Inventar und Rüstung speichern ***
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalInventory = playerInventory.getContents();
        ItemStack[] originalArmor = playerInventory.getArmorContents();
        boolean originalSilent = player.isSilent();

        // *** Inventar und Rüstung leeren ***
        playerInventory.clear();
        playerInventory.setArmorContents(new ItemStack[4]); // Leeres Array für Rüstungsslots

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
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true); // ensure flight remains enabled
                player.setFlying(true);       // keep player flying
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
        cameraPlayers.put(player.getUniqueId(), new CameraData(armorStand, hitbox, originalGameMode, originalAllowFlight, originalFlying, originalSilent, originalInventory, originalArmor));
        armorStandOwners.put(armorStand.getUniqueId(), player.getUniqueId());
        hitboxEntities.put(hitbox.getUniqueId(), player.getUniqueId());

        startArmorStandHealthCheck(player, armorStand);
        startHitboxSync(armorStand, hitbox);
        addPlayerToNoCollisionTeam(player);
    }

    private void exitCameraMode(Player player) {
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

        // *** Inventar und Rüstung wiederherstellen ***
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear(); // Sicherheitshalber leeren, falls Items hinzugefügt wurden
        playerInventory.setContents(cameraData.getOriginalInventoryContents());
        playerInventory.setArmorContents(cameraData.getOriginalArmorContents());

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.setFireTicks(0);
        player.setGameMode(cameraData.getOriginalGameMode());
        player.setAllowFlight(cameraData.getOriginalAllowFlight());
        player.setFlying(cameraData.getOriginalFlying());
        player.setSilent(cameraData.getOriginalSilent());

        removePlayerFromNoCollisionTeam(player);

        // Aufräumen
        armorStandOwners.remove(armorStand.getUniqueId());
        hitboxEntities.remove(hitbox.getUniqueId());
        armorStand.remove();
        hitbox.remove();
        cameraPlayers.remove(player.getUniqueId());
    }

    private void startArmorStandHealthCheck(Player player, ArmorStand armorStand) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cameraPlayers.containsKey(player.getUniqueId()) || !player.isOnline() || armorStand.isDead()) {
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
                        applyDamageWithArmor(player, drowningDamage);
                        if (!player.isDead()) {
                            exitCameraMode(player);
                        }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        UUID ownerUUID = null;

        if (entity instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(entity.getUniqueId());
        } else if (entity instanceof Villager) {
            ownerUUID = hitboxEntities.get(entity.getUniqueId());
        }

        if (ownerUUID == null) return;

        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner == null || !owner.isOnline()) { // Check changed to !owner.isOnline()
            if (entity instanceof ArmorStand) {
                armorStandOwners.remove(entity.getUniqueId());
            } else {
                hitboxEntities.remove(entity.getUniqueId());
            }
            cameraPlayers.remove(ownerUUID);
            entity.remove();
            return;
        }

        if (owner.isDead()){ // If owner is dead, just remove entities and cancel
            event.setCancelled(true);
            exitCameraMode(owner);
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            Entity damager = entityEvent.getDamager();

            if (damager.getUniqueId().equals(owner.getUniqueId())) {
                event.setCancelled(true);
                owner.sendMessage(getMessage("camera-off"));
                exitCameraMode(owner);
                return;
            }

            event.setCancelled(true);
            applyDamageWithArmor(owner, event.getFinalDamage());

            if (!owner.isDead()) {
                String damagerName = damager instanceof Player ?
                        ((Player) damager).getName() : damager.getType().toString();
                owner.sendMessage(getMessage("body-attacked").replace("{damager}", damagerName));
                exitCameraMode(owner);
            }
            return;
        }

        event.setCancelled(true);
        applyDamageWithArmor(owner, event.getFinalDamage());

        if (!owner.isDead()) {
            owner.sendMessage(getMessage("body-env-damage"));
            exitCameraMode(owner);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();
        Player player = event.getPlayer();
        UUID ownerUUID = armorStandOwners.get(armorStand.getUniqueId());
        if (ownerUUID == null) return;
        if (!player.getUniqueId().equals(ownerUUID)) {
            event.setCancelled(true);
            player.sendMessage(getMessage("cant-manipulate-other"));
            return;
        }
        event.setCancelled(true);
        player.sendMessage(getMessage("camera-off"));
        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner != null) {
            exitCameraMode(owner);
        }
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
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player &&
                cameraPlayers.containsKey(event.getDamager().getUniqueId())) {
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

    private String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path, ""));
    }

    private void loadConfigValues() {
        maxDistanceEnabled = getConfig().getBoolean("camera-mode.max-distance-enabled", true);
        maxDistance = getConfig().getDouble("camera-mode.max-distance", 100.0);
        distanceWarningCooldown = getConfig().getInt("camera-mode.distance-warning-cooldown", 3);
        drowningDamage = getConfig().getDouble("camera-mode.drowning-damage", 2.0);
        armorStandNameVisible = getConfig().getBoolean("armorstand.name-visible", true);
        armorStandVisible = getConfig().getBoolean("armorstand.visible", true);
        armorStandGravity = getConfig().getBoolean("armorstand.gravity", true);
    }

    private void setupNoCollisionTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(NO_COLLISION_TEAM);
        }
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
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

        public CameraData(ArmorStand armorStand, Villager hitbox, GameMode originalGameMode, boolean originalAllowFlight, boolean originalFlying, boolean originalSilent, ItemStack[] originalInventoryContents, ItemStack[] originalArmorContents) {
            this.armorStand = armorStand;
            this.hitbox = hitbox;
            this.originalGameMode = originalGameMode;
            this.originalAllowFlight = originalAllowFlight;
            this.originalFlying = originalFlying;
            this.originalSilent = originalSilent;
            this.originalInventoryContents = originalInventoryContents;
            this.originalArmorContents = originalArmorContents;
        }

        public ArmorStand getArmorStand() { return armorStand; }
        public Villager getHitbox() { return hitbox; }
        public GameMode getOriginalGameMode() { return originalGameMode; }
        public boolean getOriginalAllowFlight() { return originalAllowFlight; }
        public boolean getOriginalFlying() { return originalFlying; }
        public boolean getOriginalSilent() { return originalSilent; }
        public ItemStack[] getOriginalInventoryContents() { return originalInventoryContents; }
        public ItemStack[] getOriginalArmorContents() { return originalArmorContents; }
    }
}