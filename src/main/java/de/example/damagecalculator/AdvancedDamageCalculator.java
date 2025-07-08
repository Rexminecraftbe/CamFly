package de.example.damagecalculator;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class AdvancedDamageCalculator {

    private static final Map<String, Double> CONFIG = new HashMap<>();

    static {
        CONFIG.put("mace_fall_multiplier", 1.5);
        CONFIG.put("max_fall_damage_bonus", 50.0);
        CONFIG.put("bow_charge_minimum", 0.3);
        CONFIG.put("critical_hit_multiplier", 1.5);
        CONFIG.put("backstab_multiplier", 1.25);
    }

    public static double calculateAdvancedDamage(Player attacker, ArmorStand target, ItemStack weapon,
                                                 double fallHeight, boolean isFullyCharged,
                                                 boolean isCritical, boolean isBackstab) {

        double baseDamage = calculateBaseDamage(weapon, fallHeight, isFullyCharged);

        if (isCritical) {
            baseDamage *= CONFIG.get("critical_hit_multiplier");
        }

        if (isBackstab) {
            baseDamage *= CONFIG.get("backstab_multiplier");
        }

        double enchantedDamage = applyAdvancedEnchantments(baseDamage, weapon, target, attacker);

        double effectModifiedDamage = applyAttackerEffects(enchantedDamage, attacker);

        double armorProtection = calculateArmorProtectionWithBreach(target, weapon);

        double protectionReduction = calculateAdvancedProtection(target, weapon, attacker);

        double finalDamage = applyDamageReduction(effectModifiedDamage, armorProtection, protectionReduction);

        return Math.max(0, finalDamage);
    }

    private static double calculateBaseDamage(ItemStack weapon, double fallHeight, boolean isFullyCharged) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return 1.0;
        }

        Material weaponType = weapon.getType();
        double damage = getBaseDamageForWeapon(weaponType);

        if (weaponType == Material.MACE && fallHeight > 0) {
            double fallDamage = Math.min(fallHeight * CONFIG.get("mace_fall_multiplier"),
                    CONFIG.get("max_fall_damage_bonus"));
            damage += fallDamage;
        }

        if (weaponType == Material.BOW || weaponType == Material.CROSSBOW) {
            if (!isFullyCharged) {
                double chargeMultiplier = Math.max(CONFIG.get("bow_charge_minimum"), 1.0);
                damage *= chargeMultiplier;
            }
        }

        return damage;
    }

    private static double getBaseDamageForWeapon(Material weapon) {
        return switch (weapon) {
            case WOODEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            case WOODEN_AXE -> 7.0;
            case STONE_AXE, IRON_AXE, DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;
            case TRIDENT -> 9.0;
            case MACE -> 5.0;
            case BOW -> 9.0;
            case CROSSBOW -> 11.0;
            default -> 1.0;
        };
    }

    private static double applyAdvancedEnchantments(double baseDamage, ItemStack weapon,
                                                    ArmorStand target, Player attacker) {
        if (weapon == null || !weapon.hasItemMeta()) {
            return baseDamage;
        }

        ItemMeta meta = weapon.getItemMeta();
        double damage = baseDamage;

        if (meta.hasEnchant(Enchantment.SHARPNESS)) {
            int level = meta.getEnchantLevel(Enchantment.SHARPNESS);
            damage += 0.5 * level + 0.5;
        }

        if (meta.hasEnchant(Enchantment.BANE_OF_ARTHROPODS)) {
            if (target.hasMetadata("mob_type") &&
                    target.getMetadata("mob_type").get(0).asString().equals("arthropod")) {
                int level = meta.getEnchantLevel(Enchantment.BANE_OF_ARTHROPODS);
                damage += 2.5 * level;
            }
        }

        if (meta.hasEnchant(Enchantment.SMITE)) {
            if (target.hasMetadata("mob_type") &&
                    target.getMetadata("mob_type").get(0).asString().equals("undead")) {
                int level = meta.getEnchantLevel(Enchantment.SMITE);
                damage += 2.5 * level;
            }
        }

        if (meta.hasEnchant(Enchantment.IMPALING) && weapon.getType() == Material.TRIDENT) {
            if (target.isInWater() || target.getWorld().hasStorm()) {
                int level = meta.getEnchantLevel(Enchantment.IMPALING);
                damage += 2.5 * level;
            }
        }

        if (meta.hasEnchant(Enchantment.POWER) && weapon.getType() == Material.BOW) {
            int level = meta.getEnchantLevel(Enchantment.POWER);
            damage += (level * 0.25 + 0.25) * baseDamage;
        }

        if (meta.hasEnchant(Enchantment.PIERCING) && weapon.getType() == Material.CROSSBOW) {
            int level = meta.getEnchantLevel(Enchantment.PIERCING);
            damage += 0.5 * level;
        }

        return damage;
    }

    private static double applyAttackerEffects(double damage, Player attacker) {
        double modifiedDamage = damage;

        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            int amplifier = effect.getAmplifier() + 1;
            modifiedDamage += 3.0 * amplifier;
        }

        if (attacker.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
            int amplifier = effect.getAmplifier() + 1;
            modifiedDamage -= 4.0 * amplifier;
        }

        return Math.max(0, modifiedDamage);
    }

    private static double calculateArmorProtectionWithBreach(ArmorStand target, ItemStack weapon) {
        double totalArmor = 0;
        double totalToughness = 0;
        double breachReduction = 0;

        if (weapon != null && weapon.hasItemMeta()) {
            ItemMeta meta = weapon.getItemMeta();
            if (meta.hasEnchant(Enchantment.BREACH)) {
                int level = meta.getEnchantLevel(Enchantment.BREACH);
                breachReduction = 0.15 * level;
            }
        }

        ItemStack[] armor = target.getEquipment().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece != null && piece.getType() != Material.AIR) {
                int armorValue = getArmorValue(piece.getType());
                totalArmor += armorValue;

                if (piece.getType().name().contains("DIAMOND")) {
                    totalToughness += 2.0;
                } else if (piece.getType().name().contains("NETHERITE")) {
                    totalToughness += 3.0;
                }
            }
        }

        totalArmor *= (1 - breachReduction);
        totalToughness *= (1 - breachReduction);

        return Math.min(totalArmor, 20);
    }

    private static int getArmorValue(Material material) {
        return switch (material) {
            case LEATHER_HELMET -> 1;
            case LEATHER_CHESTPLATE -> 3;
            case LEATHER_LEGGINGS -> 2;
            case LEATHER_BOOTS -> 1;
            case CHAINMAIL_HELMET -> 2;
            case CHAINMAIL_CHESTPLATE -> 5;
            case CHAINMAIL_LEGGINGS -> 4;
            case CHAINMAIL_BOOTS -> 1;
            case IRON_HELMET -> 2;
            case IRON_CHESTPLATE -> 6;
            case IRON_LEGGINGS -> 5;
            case IRON_BOOTS -> 2;
            case DIAMOND_HELMET -> 3;
            case DIAMOND_CHESTPLATE -> 8;
            case DIAMOND_LEGGINGS -> 6;
            case DIAMOND_BOOTS -> 3;
            case NETHERITE_HELMET -> 3;
            case NETHERITE_CHESTPLATE -> 8;
            case NETHERITE_LEGGINGS -> 6;
            case NETHERITE_BOOTS -> 3;
            default -> 0;
        };
    }

    private static double calculateAdvancedProtection(ArmorStand target, ItemStack weapon, Player attacker) {
        double totalProtection = 0;

        ItemStack[] armor = target.getEquipment().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece != null && piece.hasItemMeta()) {
                ItemMeta meta = piece.getItemMeta();

                if (meta.hasEnchant(Enchantment.PROTECTION)) {
                    totalProtection += meta.getEnchantLevel(Enchantment.PROTECTION);
                }

                if (weapon != null) {
                    if (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW) {
                        if (meta.hasEnchant(Enchantment.PROJECTILE_PROTECTION)) {
                            totalProtection += meta.getEnchantLevel(Enchantment.PROJECTILE_PROTECTION) * 2;
                        }
                    }
                }

                if (meta.hasEnchant(Enchantment.BLAST_PROTECTION)) {
                    if (hasExplosionDamage(weapon)) {
                        totalProtection += meta.getEnchantLevel(Enchantment.BLAST_PROTECTION) * 2;
                    }
                }

                if (meta.hasEnchant(Enchantment.FIRE_PROTECTION)) {
                    if (hasFireDamage(weapon)) {
                        totalProtection += meta.getEnchantLevel(Enchantment.FIRE_PROTECTION) * 2;
                    }
                }
            }
        }

        return Math.min(totalProtection, 20) * 0.04;
    }

    private static boolean hasExplosionDamage(ItemStack weapon) {
        return false;
    }

    private static boolean hasFireDamage(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = weapon.getItemMeta();
        return meta.hasEnchant(Enchantment.FIRE_ASPECT);
    }

    private static double applyDamageReduction(double damage, double armor, double protection) {
        double armorReduction = Math.min(20, Math.max(armor / 5, armor - damage / (2 + armor / 25)));
        double damageAfterArmor = damage * (1 - armorReduction / 25);

        double finalDamage = damageAfterArmor * (1 - protection);

        return Math.max(0, finalDamage);
    }

    public static boolean isCriticalHit(Player attacker) {
        return attacker.getFallDistance() > 0 &&
                !attacker.isOnGround() &&
                !attacker.isInWater() &&
                !attacker.isClimbing();
    }

    public static boolean isBackstab(Player attacker, ArmorStand target) {
        double attackerYaw = attacker.getLocation().getYaw();
        double targetYaw = target.getLocation().getYaw();
        double angleDifference = Math.abs(attackerYaw - targetYaw);
        if (angleDifference > 180) {
            angleDifference = 360 - angleDifference;
        }
        return angleDifference < 45;
    }

    public static void setConfigValue(String key, double value) {
        CONFIG.put(key, value);
    }

    public static double getConfigValue(String key) {
        return CONFIG.getOrDefault(key, 0.0);
    }
}