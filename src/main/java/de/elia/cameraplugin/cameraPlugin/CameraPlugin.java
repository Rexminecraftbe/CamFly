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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;

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

    @Override
    public void onEnable() {
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
            sender.sendMessage("§cDieser Befehl kann nur von Spielern verwendet werden!");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("cam")) {
            if (cameraPlayers.containsKey(player.getUniqueId())) {
                exitCameraMode(player);
                player.sendMessage("§aKamera-Modus beendet.");
            } else {
                enterCameraMode(player);
                player.sendMessage("§aKamera-Modus aktiviert! Nutze /cam erneut zum Beenden.");
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

        // *** Inventar und Rüstung leeren ***
        playerInventory.clear();
        playerInventory.setArmorContents(new ItemStack[4]); // Leeres Array für Rüstungsslots

        Location playerLocation = player.getLocation();

        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(playerLocation, EntityType.ARMOR_STAND);
        armorStand.setVisible(true);
        armorStand.setGravity(true);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomName("§e" + player.getName() + "'s Körper");
        armorStand.setCustomNameVisible(true);
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
        hitbox.setCustomName("§c[HITBOX] " + player.getName());
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

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // *** Gespeichertes Inventar an CameraData übergeben ***
        cameraPlayers.put(player.getUniqueId(), new CameraData(armorStand, hitbox, originalGameMode, originalAllowFlight, originalFlying, originalInventory, originalArmor));
        armorStandOwners.put(armorStand.getUniqueId(), player.getUniqueId());
        hitboxEntities.put(hitbox.getUniqueId(), player.getUniqueId());

        startArmorStandHealthCheck(player, armorStand);
        startHitboxSync(armorStand, hitbox);
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
                    player.sendMessage("§cDein Körper erstickt! Kamera-Modus wird beendet.");
                    exitCameraMode(player);
                    this.cancel();
                }
                if (armorStand.getRemainingAir() < armorStand.getMaximumAir() && armorStand.getRemainingAir() <= 0) {
                    if (armorStand.getTicksLived() % 20 == 0) {
                        player.sendMessage("§cDein Körper ertrinkt!");
                        applyDamageWithArmor(player, 2.0);
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
                owner.sendMessage("§aKamera-Modus beendet.");
                exitCameraMode(owner);
                return;
            }

            event.setCancelled(true);
            applyDamageWithArmor(owner, event.getFinalDamage());

            if (!owner.isDead()) {
                String damagerName = damager instanceof Player ?
                        ((Player) damager).getName() : damager.getType().toString();
                owner.sendMessage("§cDein Körper wurde von " + damagerName + " angegriffen! Kamera-Modus beendet.");
                exitCameraMode(owner);
            }
            return;
        }

        event.setCancelled(true);
        applyDamageWithArmor(owner, event.getFinalDamage());

        if (!owner.isDead()) {
            owner.sendMessage("§cDein Körper wurde durch Umweltschaden verletzt! Kamera-Modus beendet.");
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
            player.sendMessage("§cDu kannst den Körper eines anderen Spielers nicht manipulieren!");
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§aKamera-Modus beendet.");
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
                    player.sendMessage("§aKamera-Modus beendet.");
                    Player owner = Bukkit.getPlayer(ownerUUID);
                    if (owner != null) {
                        exitCameraMode(owner);
                    }
                } else {
                    player.sendMessage("§cDu kannst mit dem Körper eines anderen Spielers nicht interagieren!");
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
            shooter.sendMessage("§cDu kannst im Kamera-Modus keine Projektile verwenden.");
        }
    }

    // ##### HIER IST DIE ÄNDERUNG #####
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!cameraPlayers.containsKey(player.getUniqueId())) {
            return;
        }

        Action action = event.getAction();

        // Verhindert das Benutzen von fast allen Items (Rechtsklick)
        // UND das Auslösen von Druckplatten und Stolperdrähten (Physical)
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK || action == Action.PHYSICAL) {
            event.setCancelled(true);
        }
        // Anmerkung: Linksklick (Blockzerstörung) wird bereits durch den Adventure-Modus verhindert.
    }
    // ##### ENDE DER ÄNDERUNG #####

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
            player.sendMessage("§cDu kannst nicht durch Lava fliegen! Kamera-Modus wird beendet.");
            exitCameraMode(player);
            return;
        }

        Location standLoc = cameraPlayers.get(player.getUniqueId()).getArmorStand().getLocation();
        if (!to.getWorld().equals(standLoc.getWorld()) || to.distanceSquared(standLoc) > 100 * 100) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (distanceMessageCooldown.getOrDefault(player.getUniqueId(), 0L) < now) {
                player.sendMessage("§cDu kannst dich nicht weiter als 100 Blöcke von deinem Körper entfernen!");
                distanceMessageCooldown.put(player.getUniqueId(), now + TimeUnit.SECONDS.toMillis(3));
            }
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

    // *** CameraData Klasse erweitert ***
    private static class CameraData {
        private final ArmorStand armorStand;
        private final Villager hitbox;
        private final GameMode originalGameMode;
        private final boolean originalAllowFlight;
        private final boolean originalFlying;
        private final ItemStack[] originalInventoryContents; // Für Inventar
        private final ItemStack[] originalArmorContents;     // Für Rüstung

        public CameraData(ArmorStand armorStand, Villager hitbox, GameMode originalGameMode, boolean originalAllowFlight, boolean originalFlying, ItemStack[] originalInventoryContents, ItemStack[] originalArmorContents) {
            this.armorStand = armorStand;
            this.hitbox = hitbox;
            this.originalGameMode = originalGameMode;
            this.originalAllowFlight = originalAllowFlight;
            this.originalFlying = originalFlying;
            this.originalInventoryContents = originalInventoryContents;
            this.originalArmorContents = originalArmorContents;
        }

        public ArmorStand getArmorStand() { return armorStand; }
        public Villager getHitbox() { return hitbox; }
        public GameMode getOriginalGameMode() { return originalGameMode; }
        public boolean getOriginalAllowFlight() { return originalAllowFlight; }
        public boolean getOriginalFlying() { return originalFlying; }
        public ItemStack[] getOriginalInventoryContents() { return originalInventoryContents; }
        public ItemStack[] getOriginalArmorContents() { return originalArmorContents; }
    }
}