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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.SoundCategory;
import de.elia.cameraplugin.cameraPlugin.CamCommand;
import de.elia.cameraplugin.cameraPlugin.CamTabCompleter;
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

@SuppressWarnings("removal")
public final class CameraPlugin extends JavaPlugin implements Listener {

    // Speichert Daten für Spieler im Kameramodus (z.B. Originalinventar, ArmorStand)
    private final Map<UUID, CameraData> cameraPlayers = new HashMap<>();
    // Verhindert Spam von Distanzwarnungen
    private final Map<UUID, Long> distanceMessageCooldown = new HashMap<>();
    // Erlaubt es, dem Spieler Schaden zuzufügen, ohne dass unser eigener onPlayerDamage-Handler ihn abbricht
    private final Set<UUID> damageImmunityBypass = new HashSet<>();
    // Ordnet ArmorStands ihren Spielern zu, um Schaden korrekt weiterzuleiten
    private final Map<UUID, UUID> armorStandOwners = new HashMap<>();
    // Ordnet die unsichtbare Hitbox (Villager) ihrem Spieler zu
    private final Map<UUID, UUID> hitboxEntities = new HashMap<>();

    // Name des Teams, das Kollisionen für Spieler im Kameramodus deaktiviert
    private static final String NO_COLLISION_TEAM = "cam_no_push";

    // Konfigurierbare Werte aus der config.yml
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
        this.getCommand("cam").setExecutor(new CamCommand(this));
        this.getCommand("cam").setTabCompleter(new CamTabCompleter());
        this.getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CameraPlugin wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        // Beendet den Kameramodus für alle Spieler, um Fehler beim Deaktivieren zu vermeiden
        for (UUID playerId : new HashSet<>(cameraPlayers.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                exitCameraMode(player);
            }
        }
        getLogger().info("CameraPlugin wurde deaktiviert!");
    }

    /**
     * Versetzt einen Spieler in den Kameramodus.
     * Speichert sein Inventar, erstellt einen ArmorStand als Körper und macht den Spieler unsichtbar.
     * @param player Der Spieler, der in den Modus wechseln soll.
     */
    public void enterCameraMode(Player player) {
        // Speichere den aktuellen Zustand des Spielers
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalInventory = playerInventory.getContents();
        ItemStack[] originalArmor = playerInventory.getArmorContents();
        boolean originalSilent = player.isSilent();

        // Leere das Inventar und die Rüstung des Spielers
        playerInventory.clear();
        playerInventory.setArmorContents(new ItemStack[4]);
        player.updateInventory();

        Location playerLocation = player.getLocation();

        // Erstelle den ArmorStand, der den Körper des Spielers repräsentiert
        ArmorStand armorStand = player.getWorld().spawn(playerLocation, ArmorStand.class, as -> {
            as.setVisible(armorStandVisible);
            as.setGravity(armorStandGravity);
            as.setCanPickupItems(false);
            as.setCustomName(getMessage("armorstand.name-format").replace("{player}", player.getName()));
            as.setCustomNameVisible(armorStandNameVisible);
            as.setInvulnerable(false); // Muss Schaden nehmen können
            as.setMarker(false);
            as.setMaxHealth(20.0);
            as.setHealth(20.0);
            // Sperrt die Ausrüstung, damit sie nicht entfernt werden kann
            as.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
            as.addEquipmentLock(EquipmentSlot.CHEST, ArmorStand.LockType.REMOVING_OR_CHANGING);
            as.addEquipmentLock(EquipmentSlot.LEGS, ArmorStand.LockType.REMOVING_OR_CHANGING);
            as.addEquipmentLock(EquipmentSlot.FEET, ArmorStand.LockType.REMOVING_OR_CHANGING);
            as.addEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);
            as.addEquipmentLock(EquipmentSlot.OFF_HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);
        });

        // Gib dem ArmorStand den Kopf des Spielers
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            playerHead.setItemMeta(skullMeta);
        }
        armorStand.getEquipment().setHelmet(playerHead);

        // Gib dem ArmorStand die Rüstung des Spielers
        armorStand.getEquipment().setArmorContents(originalArmor);

        // Erstelle eine unsichtbare Hitbox, auf die Mobs zielen können
        Villager hitbox = player.getWorld().spawn(playerLocation, Villager.class, v -> {
            v.setInvisible(true);
            v.setSilent(true);
            v.setAI(false);
            v.setInvulnerable(true); // Die Hitbox selbst soll keinen Schaden nehmen
            v.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            v.setProfession(Villager.Profession.NONE);
            v.setVillagerType(Villager.Type.PLAINS);
            v.setCanPickupItems(false);
        });

        // Speichere den Originalzustand des Spielers (Gamemode, Flugstatus)
        GameMode originalGameMode = player.getGameMode();
        boolean originalAllowFlight = player.getAllowFlight();
        boolean originalFlying = player.isFlying();

        // Ändere den Zustand des Spielers für den Kameramodus
        player.setGameMode(GameMode.ADVENTURE); // Adventure, um Interaktion zu verhindern
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setSilent(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // Lenke Mobs, die den Spieler anvisiert haben, auf die neue Hitbox um
        for (Entity entity : player.getNearbyEntities(64, 64, 64)) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(hitbox);
            }
        }

        // Speichere alle relevanten Daten in der cameraPlayers Map
        cameraPlayers.put(player.getUniqueId(), new CameraData(armorStand, hitbox, originalGameMode, originalAllowFlight, originalFlying, originalSilent, originalInventory, originalArmor));
        armorStandOwners.put(armorStand.getUniqueId(), player.getUniqueId());
        hitboxEntities.put(hitbox.getUniqueId(), player.getUniqueId());

        startHitboxSync(armorStand, hitbox);
        addPlayerToNoCollisionTeam(player);
    }

    /**
     * Beendet den Kameramodus für einen Spieler und stellt seinen Originalzustand wieder her.
     * @param player Der Spieler, der den Modus verlassen soll.
     */
    public void exitCameraMode(Player player) {
        CameraData cameraData = cameraPlayers.get(player.getUniqueId());
        if (cameraData == null) return;

        ArmorStand armorStand = cameraData.getArmorStand();
        Villager hitbox = cameraData.getHitbox();

        // Lenke Mobs wieder auf den Spieler um
        for (Entity entity : armorStand.getNearbyEntities(64, 64, 64)) {
            if (entity instanceof Mob mob && (armorStand.equals(mob.getTarget()) || hitbox.equals(mob.getTarget()))) {
                mob.setTarget(player);
            }
        }

        // Teleportiere Spieler zum Körper, bevor der Zustand wiederhergestellt wird
        player.teleport(armorStand.getLocation());
        // Synchronisiere die Haltbarkeit der Rüstung zurück zum Spieler
        syncArmorBack(player, armorStand, cameraData.getOriginalArmorContents());

        // Stelle das Inventar und die Rüstung des Spielers wieder her
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();
        playerInventory.setContents(cameraData.getOriginalInventoryContents());
        player.updateInventory(); // Wichtig, damit die Rüstung sofort wiederhergestellt wird

        // Entferne Effekte und stelle den Originalzustand wieder her
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.setFireTicks(0);
        player.setGameMode(cameraData.getOriginalGameMode());
        player.setAllowFlight(cameraData.getOriginalAllowFlight());
        player.setFlying(cameraData.getOriginalFlying());
        player.setSilent(cameraData.getOriginalSilent());

        removePlayerFromNoCollisionTeam(player);

        // Räume die erstellten Entities und Daten auf
        armorStandOwners.remove(armorStand.getUniqueId());
        hitboxEntities.remove(hitbox.getUniqueId());
        armorStand.remove();
        hitbox.remove();
        cameraPlayers.remove(player.getUniqueId());
    }

    /**
     * Prüft, ob sich ein Spieler im Kameramodus befindet.
     * @param player Der zu prüfende Spieler.
     * @return true, wenn der Spieler im Kameramodus ist.
     */
    public boolean isInCameraMode(Player player) {
        return cameraPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Synchronisiert die Position der Hitbox mit der des ArmorStands.
     */
    private void startHitboxSync(ArmorStand armorStand, Villager hitbox) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Stoppe, wenn der ArmorStand oder die Hitbox nicht mehr existiert
                if (armorStand.isDead() || hitbox.isDead() || !cameraPlayers.containsKey(armorStandOwners.get(armorStand.getUniqueId()))) {
                    this.cancel();
                    return;
                }
                hitbox.teleport(armorStand.getLocation().add(0, 0.1, 0));
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    /**
     * Behandelt Schaden, der dem ArmorStand oder der Hitbox zugefügt wird.
     * Leitet den Schaden an den Spieler weiter und beendet den Kameramodus.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandDamage(EntityDamageEvent event) {
        Entity damagedEntity = event.getEntity();
        UUID ownerUUID = null;

        // Finde heraus, ob die beschädigte Entity ein Körper (ArmorStand) eines Spielers ist
        if (damagedEntity instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(damagedEntity.getUniqueId());
        } else if (damagedEntity instanceof Villager) {
            // Schaden an der Hitbox wird ebenfalls behandelt (sollte aber invulnerable sein)
            ownerUUID = hitboxEntities.get(damagedEntity.getUniqueId());
        }

        if (ownerUUID == null) return; // Nicht unser ArmorStand/Hitbox

        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner == null || !owner.isOnline()) {
            // Wenn der Spieler offline ist, räume die Entities auf
            if (damagedEntity instanceof ArmorStand) armorStandOwners.remove(damagedEntity.getUniqueId());
            else hitboxEntities.remove(damagedEntity.getUniqueId());
            cameraPlayers.remove(ownerUUID);
            damagedEntity.remove();
            return;
        }

        if (owner.isDead()) {
            event.setCancelled(true);
            exitCameraMode(owner);
            return;
        }

        // Verhindere, dass der ArmorStand tatsächlich Schaden nimmt oder stirbt
        event.setCancelled(true);

        // Wenn der Spieler seinen eigenen Körper schlägt, beende den Modus
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            if (entityEvent.getDamager().getUniqueId().equals(owner.getUniqueId())) {
                owner.sendMessage(getMessage("camera-off"));
                exitCameraMode(owner);
                return;
            }
        }

        // Hol den rohen Schaden, bevor irgendwelche Reduktionen angewendet wurden
        double originalDamage = event.getDamage();
        ArmorStand armorStand = cameraPlayers.get(ownerUUID).getArmorStand();

        // Berechne den Schaden neu, basierend auf der Rüstung des ArmorStands
        double reducedDamage = calculateArmorReducedDamage(originalDamage, armorStand);

        // Hol den Namen des Angreifers, falls vorhanden
        String damagerName = "Umgebung";
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            Entity damager = entityEvent.getDamager();
            damagerName = damager instanceof Player ? damager.getName() : damager.getType().toString();
        }

        // Beende den Kameramodus
        exitCameraMode(owner);

        // Wende den berechneten Schaden nach einer kleinen Verzögerung auf den Spieler an
        String finalDamagerName = damagerName;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (owner.isOnline() && !owner.isDead()) {
                    applyDirectDamage(owner, reducedDamage);
                    String messageKey = event instanceof EntityDamageByEntityEvent ? "body-attacked" : "body-env-damage";
                    owner.sendMessage(getMessage(messageKey).replace("{damager}", finalDamagerName));
                }
            }
        }.runTaskLater(CameraPlugin.this, 1L);
    }

    // ... (Andere Event-Handler wie onPlayerInteract, onPlayerMove, etc. bleiben größtenteils gleich)
    // Ich habe die wichtigsten neuen Methoden unten hinzugefügt und die alten ersetzt/aktualisiert.

    /**
     * Berechnet den Schaden nach Rüstungsreduktion anhand der Ausrüstung des ArmorStands.
     * Diese Methode simuliert die Schadensberechnung eines normalen Spielers.
     * @param originalDamage Der ursprüngliche Schaden vor Rüstungsreduktion.
     * @param armorStand Der ArmorStand, dessen Rüstung verwendet wird.
     * @return Der reduzierte Schaden.
     */
    private double calculateArmorReducedDamage(double originalDamage, ArmorStand armorStand) {
        ItemStack[] armor = armorStand.getEquipment().getArmorContents();
        int armorPoints = 0;
        int toughness = 0;

        for (ItemStack item : armor) {
            if (item == null || item.getType() == Material.AIR) continue;
            // Bestimme Rüstungspunkte und Zähigkeit basierend auf dem Material
            switch (item.getType()) {
                case LEATHER_HELMET -> armorPoints += 1;
                case LEATHER_CHESTPLATE, ELYTRA -> armorPoints += 3;
                case LEATHER_LEGGINGS -> armorPoints += 2;
                case LEATHER_BOOTS -> armorPoints += 1;

                case CHAINMAIL_HELMET -> armorPoints += 2;
                case CHAINMAIL_CHESTPLATE -> armorPoints += 5;
                case CHAINMAIL_LEGGINGS -> armorPoints += 4;
                case CHAINMAIL_BOOTS -> armorPoints += 1;

                case IRON_HELMET -> armorPoints += 2;
                case IRON_CHESTPLATE -> armorPoints += 6;
                case IRON_LEGGINGS -> armorPoints += 5;
                case IRON_BOOTS -> armorPoints += 2;

                case GOLDEN_HELMET -> armorPoints += 2;
                case GOLDEN_CHESTPLATE -> armorPoints += 5;
                case GOLDEN_LEGGINGS -> armorPoints += 3;
                case GOLDEN_BOOTS -> armorPoints += 1;

                case DIAMOND_HELMET -> { armorPoints += 3; toughness += 2; }
                case DIAMOND_CHESTPLATE -> { armorPoints += 8; toughness += 2; }
                case DIAMOND_LEGGINGS -> { armorPoints += 6; toughness += 2; }
                case DIAMOND_BOOTS -> { armorPoints += 3; toughness += 2; }

                case NETHERITE_HELMET -> { armorPoints += 3; toughness += 3; }
                case NETHERITE_CHESTPLATE -> { armorPoints += 8; toughness += 3; }
                case NETHERITE_LEGGINGS -> { armorPoints += 6; toughness += 3; }
                case NETHERITE_BOOTS -> { armorPoints += 3; toughness += 3; }

                default -> {}
            }
        }

        // Vanilla Minecraft Schadensformel für Rüstung
        float damageReduction = (float) (armorPoints * 0.04); // Jeder Rüstungspunkt blockt 4% Schaden
        float toughDamage = (float) (originalDamage * (1.0 - toughness * 0.04));

        double damageAfterToughness = originalDamage * (1 - Math.min(20.0, Math.max(armorPoints / 5.0, armorPoints - originalDamage / (2.0 + toughness / 4.0))) / 25.0);
        return Math.max(0, damageAfterToughness);
    }

    /**
     * Wendet Schaden direkt auf die Lebenspunkte des Spielers an und umgeht dabei die erneute
     * Rüstungsberechnung des Spielers sowie die standardmäßige Unverwundbarkeitszeit.
     * @param player Der Spieler, der Schaden erleiden soll.
     * @param damage Die Höhe des Schadens.
     */
    private void applyDirectDamage(Player player, double damage) {
        if (damage <= 0 || player.isDead()) return;

        // Füge den Spieler zur Bypass-Liste hinzu, damit unser onPlayerDamage Event den Schaden nicht blockiert
        damageImmunityBypass.add(player.getUniqueId());
        try {
            // Berechne die neuen Lebenspunkte, stelle sicher, dass sie nicht unter 0 fallen
            double newHealth = Math.max(0, player.getHealth() - damage);
            player.setHealth(newHealth);

            // Erstelle ein "Fake" Damage-Event, damit Todesnachrichten und andere Plugins korrekt funktionieren
            EntityDamageEvent damageEvent = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.CUSTOM, damage);
            damageEvent.setDamage(EntityDamageEvent.DamageModifier.BASE, damage);
            player.setLastDamageCause(damageEvent);
        } finally {
            // Entferne den Spieler nach einem Tick wieder aus der Bypass-Liste
            new BukkitRunnable() {
                @Override
                public void run() {
                    damageImmunityBypass.remove(player.getUniqueId());
                }
            }.runTaskLater(this, 1L);
        }
    }

    /**
     * Überträgt die Haltbarkeit der Rüstungsteile vom ArmorStand zurück auf die
     * ursprünglichen Rüstungs-ItemStacks des Spielers.
     * @param player Der Spieler.
     * @param armorStand Der ArmorStand mit der beschädigten Rüstung.
     * @param originalArmor Die ursprünglichen Rüstungsitems des Spielers.
     */
    private void syncArmorBack(Player player, ArmorStand armorStand, ItemStack[] originalArmor) {
        ItemStack[] standArmor = armorStand.getEquipment().getArmorContents();
        for (int i = 0; i < originalArmor.length; i++) {
            ItemStack original = originalArmor[i];
            ItemStack fromStand = standArmor.length > i ? standArmor[i] : null;

            // Stelle sicher, dass beide Items existieren und vom gleichen Typ sind
            if (original != null && fromStand != null && original.getType() == fromStand.getType()) {
                ItemMeta standMeta = fromStand.getItemMeta();
                ItemMeta originalMeta = original.getItemMeta();
                // Übertrage den Schaden (Haltbarkeitsverlust)
                if (standMeta instanceof Damageable && originalMeta instanceof Damageable) {
                    int damage = ((Damageable) standMeta).getDamage();
                    ((Damageable) originalMeta).setDamage(damage);
                    original.setItemMeta(originalMeta);
                }
            }
        }
        // Gib dem Spieler die aktualisierte Rüstung zurück (technisch gesehen nicht nötig, da originalArmor eine Referenz ist, aber zur Sicherheit)
        player.getInventory().setArmorContents(originalArmor);
    }

    // Der Rest deiner Klasse (onPlayerQuit, onPlayerMove, etc.) kann hier folgen.
    // Ich füge die restlichen Methoden aus deinem Originalcode hinzu, damit es vollständig ist.

    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager && hitboxEntities.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (cameraPlayers.containsKey(player.getUniqueId())) {
            CameraData data = cameraPlayers.get(player.getUniqueId());
            if (data != null && data.getHitbox() != null && !data.getHitbox().isDead()) {
                event.setTarget(data.getHitbox());
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player shooter && cameraPlayers.containsKey(shooter.getUniqueId())) {
            event.setCancelled(true);
            shooter.sendMessage(getMessage("no-projectiles"));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (cameraPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && cameraPlayers.containsKey(event.getEntity().getUniqueId()) && !damageImmunityBypass.contains(event.getEntity().getUniqueId())) {
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

        if (to.getBlock().getType() == Material.LAVA) {
            player.sendMessage(getMessage("cant-fly-in-lava"));
            exitCameraMode(player);
            return;
        }

        Location standLoc = cameraPlayers.get(player.getUniqueId()).getArmorStand().getLocation();
        if (!to.getWorld().equals(standLoc.getWorld()) || (maxDistanceEnabled && to.distanceSquared(standLoc) > maxDistance * maxDistance)) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (distanceMessageCooldown.getOrDefault(player.getUniqueId(), 0L) < now) {
                player.sendMessage(getMessage("distance-limit").replace("{distance}", String.valueOf(maxDistance)));
                distanceMessageCooldown.put(player.getUniqueId(), now + TimeUnit.SECONDS.toMillis(distanceWarningCooldown));
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (cameraPlayers.containsKey(event.getEntity().getUniqueId())) {
            Player player = event.getEntity();
            CameraData data = cameraPlayers.get(player.getUniqueId());

            event.getDrops().clear();

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
        if (event.getEntered() instanceof Player && cameraPlayers.containsKey(event.getEntered().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && cameraPlayers.containsKey(event.getEntity().getUniqueId())) {
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && cameraPlayers.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Hilfsmethoden

    public String getMessage(String path) {
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
        Bukkit.getScoreboardManager().getMainScoreboard().getTeam(NO_COLLISION_TEAM).addEntry(player.getName());
    }

    private void removePlayerFromNoCollisionTeam(Player player) {
        Bukkit.getScoreboardManager().getMainScoreboard().getTeam(NO_COLLISION_TEAM).removeEntry(player.getName());
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
    }

    /**
     * Innere Klasse zur Speicherung der Originaldaten eines Spielers im Kameramodus.
     */
    private static class CameraData {
        private final ArmorStand armorStand;
        private final Villager hitbox;
        private final GameMode originalGameMode;
        private final boolean originalAllowFlight;
        private final boolean originalFlying;
        private final boolean originalSilent;
        private final ItemStack[] originalInventoryContents;
        private final ItemStack[] originalArmorContents;

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