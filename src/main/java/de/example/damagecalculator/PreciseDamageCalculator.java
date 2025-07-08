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

public class PreciseDamageCalculator {

    // Basis-Schadenswerte für Waffen in 1.21
    private static final Map<Material, Double> BASE_DAMAGE = new HashMap<>();

    static {
        // Schwerter
        BASE_DAMAGE.put(Material.WOODEN_SWORD, 4.0);
        BASE_DAMAGE.put(Material.STONE_SWORD, 5.0);
        BASE_DAMAGE.put(Material.IRON_SWORD, 6.0);
        BASE_DAMAGE.put(Material.DIAMOND_SWORD, 7.0);
        BASE_DAMAGE.put(Material.NETHERITE_SWORD, 8.0);

        // Äxte
        BASE_DAMAGE.put(Material.WOODEN_AXE, 7.0);
        BASE_DAMAGE.put(Material.STONE_AXE, 9.0);
        BASE_DAMAGE.put(Material.IRON_AXE, 9.0);
        BASE_DAMAGE.put(Material.DIAMOND_AXE, 9.0);
        BASE_DAMAGE.put(Material.NETHERITE_AXE, 10.0);

        // Dreizack
        BASE_DAMAGE.put(Material.TRIDENT, 9.0);

        // Mace (neue Waffe in 1.21)
        BASE_DAMAGE.put(Material.MACE, 5.0);

        // Bogen (variiert je nach Zugkraft)
        BASE_DAMAGE.put(Material.BOW, 9.0); // Maximum bei voller Zugkraft
        BASE_DAMAGE.put(Material.CROSSBOW, 9.0);
    }

    // Rüstungswerte für verschiedene Materialien
    private static final Map<Material, Integer> ARMOR_VALUES = new HashMap<>();

    static {
        // Leder
        ARMOR_VALUES.put(Material.LEATHER_HELMET, 1);
        ARMOR_VALUES.put(Material.LEATHER_CHESTPLATE, 3);
        ARMOR_VALUES.put(Material.LEATHER_LEGGINGS, 2);
        ARMOR_VALUES.put(Material.LEATHER_BOOTS, 1);

        // Kette
        ARMOR_VALUES.put(Material.CHAINMAIL_HELMET, 2);
        ARMOR_VALUES.put(Material.CHAINMAIL_CHESTPLATE, 5);
        ARMOR_VALUES.put(Material.CHAINMAIL_LEGGINGS, 4);
        ARMOR_VALUES.put(Material.CHAINMAIL_BOOTS, 1);

        // Eisen
        ARMOR_VALUES.put(Material.IRON_HELMET, 2);
        ARMOR_VALUES.put(Material.IRON_CHESTPLATE, 6);
        ARMOR_VALUES.put(Material.IRON_LEGGINGS, 5);
        ARMOR_VALUES.put(Material.IRON_BOOTS, 2);

        // Diamant
        ARMOR_VALUES.put(Material.DIAMOND_HELMET, 3);
        ARMOR_VALUES.put(Material.DIAMOND_CHESTPLATE, 8);
        ARMOR_VALUES.put(Material.DIAMOND_LEGGINGS, 6);
        ARMOR_VALUES.put(Material.DIAMOND_BOOTS, 3);

        // Netherit
        ARMOR_VALUES.put(Material.NETHERITE_HELMET, 3);
        ARMOR_VALUES.put(Material.NETHERITE_CHESTPLATE, 8);
        ARMOR_VALUES.put(Material.NETHERITE_LEGGINGS, 6);
        ARMOR_VALUES.put(Material.NETHERITE_BOOTS, 3);
    }

    /**
     * Berechnet den genauen Schaden von einem Angreifer auf einen Armor Stand
     */
    public static double calculateDamage(Player attacker, ArmorStand target, ItemStack weapon,
                                         double fallHeight, boolean isFullyCharged) {

        // 1. Basis-Schaden der Waffe berechnen
        double baseDamage = calculateBaseDamage(weapon, fallHeight, isFullyCharged);

        // 2. Verzauberungen der Waffe berücksichtigen
        double enchantedDamage = applyWeaponEnchantments(baseDamage, weapon, target);

        // 3. Stärke-Effekte des Angreifers
        double strengthModifiedDamage = applyStrengthEffects(enchantedDamage, attacker);

        // 4. Rüstungsschutz berechnen
        double armorProtection = calculateArmorProtection(target);

        // 5. Schutz-Verzauberungen der Rüstung
        double protectionReduction = calculateProtectionEnchantments(target, weapon);

        // 6. Finalen Schaden berechnen
        double finalDamage = applyDamageReduction(strengthModifiedDamage, armorProtection, protectionReduction);

        return Math.max(0, finalDamage);
    }

    private static double calculateBaseDamage(ItemStack weapon, double fallHeight, boolean isFullyCharged) {
        if (weapon == null || weapon.getType() == Material.AIR) {
            return 1.0; // Faust-Schaden
        }

        Material weaponType = weapon.getType();
        double damage = BASE_DAMAGE.getOrDefault(weaponType, 1.0);

        // Spezielle Behandlung für Mace mit Fallhöhe
        if (weaponType == Material.MACE && fallHeight > 0) {
            // Mace macht zusätzlichen Schaden basierend auf Fallhöhe
            double fallDamage = fallHeight * 1.5; // 1.5 Schaden pro Block Fallhöhe
            damage += fallDamage;
        }

        // Bogen-Schaden basierend auf Zugkraft
        if (weaponType == Material.BOW || weaponType == Material.CROSSBOW) {
            if (!isFullyCharged) {
                damage *= 0.5; // Halber Schaden bei nicht voll gespanntem Bogen
            }
        }

        return damage;
    }

    private static double applyWeaponEnchantments(double baseDamage, ItemStack weapon, ArmorStand target) {
        if (weapon == null || !weapon.hasItemMeta()) {
            return baseDamage;
        }

        ItemMeta meta = weapon.getItemMeta();
        double damage = baseDamage;

        // Schärfe (Sharpness)
        if (meta.hasEnchant(Enchantment.SHARPNESS)) {
            int level = meta.getEnchantLevel(Enchantment.SHARPNESS);
            damage += 0.5 * level + 0.5; // 1 + 0.5 * level zusätzlicher Schaden
        }

        // Power (Bogen)
        if (meta.hasEnchant(Enchantment.POWER)) {
            int level = meta.getEnchantLevel(Enchantment.POWER);
            damage += (level * 0.25 + 0.25) * baseDamage; // 25% + 25% pro Level
        }

        return damage;
    }

    private static double applyStrengthEffects(double damage, Player attacker) {
        double modifiedDamage = damage;

        // Stärke-Effekt
        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            int amplifier = effect.getAmplifier() + 1;
            modifiedDamage += 3.0 * amplifier;
        }

        // Schwäche-Effekt
        if (attacker.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
            int amplifier = effect.getAmplifier() + 1;
            modifiedDamage -= 4.0 * amplifier;
        }

        return Math.max(0, modifiedDamage);
    }

    private static double calculateArmorProtection(ArmorStand target) {
        double totalArmor = 0;
        double totalToughness = 0;

        ItemStack[] armor = target.getEquipment().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece != null && piece.getType() != Material.AIR) {
                totalArmor += ARMOR_VALUES.getOrDefault(piece.getType(), 0);

                // Toughness für Diamant/Netherit
                if (piece.getType().name().contains("DIAMOND")) {
                    totalToughness += getToughness(piece.getType());
                } else if (piece.getType().name().contains("NETHERITE")) {
                    totalToughness += getToughness(piece.getType());
                }
            }
        }

        return Math.min(totalArmor, 20); // Maximum 20 Rüstungspunkte
    }

    private static double getToughness(Material material) {
        if (material.name().contains("DIAMOND")) {
            return 2.0;
        } else if (material.name().contains("NETHERITE")) {
            return 3.0;
        }
        return 0.0;
    }

    private static double calculateProtectionEnchantments(ArmorStand target, ItemStack weapon) {
        double totalProtection = 0;

        ItemStack[] armor = target.getEquipment().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece != null && piece.hasItemMeta()) {
                ItemMeta meta = piece.getItemMeta();

                // Allgemeiner Schutz
                if (meta.hasEnchant(Enchantment.PROTECTION)) {
                    totalProtection += meta.getEnchantLevel(Enchantment.PROTECTION);
                }

                // Explosionsschutz
                if (meta.hasEnchant(Enchantment.BLAST_PROTECTION)) {
                    totalProtection += meta.getEnchantLevel(Enchantment.BLAST_PROTECTION);
                }

                // Projektilschutz
                if (meta.hasEnchant(Enchantment.PROJECTILE_PROTECTION)) {
                    if (weapon != null && (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW)) {
                        totalProtection += meta.getEnchantLevel(Enchantment.PROJECTILE_PROTECTION);
                    }
                }
            }
        }

        return Math.min(totalProtection, 20) * 0.04; // 4% pro Schutzlevel, max 80%
    }

    private static double applyDamageReduction(double damage, double armor, double protection) {
        // Minecraft's Schadens-Reduktions-Formel
        double armorReduction = Math.min(20, Math.max(armor / 5, armor - damage / (2 + armor / 25)));
        double damageAfterArmor = damage * (1 - armorReduction / 25);

        // Schutz-Verzauberungen anwenden
        double finalDamage = damageAfterArmor * (1 - protection);

        return Math.max(0, finalDamage);
    }
}