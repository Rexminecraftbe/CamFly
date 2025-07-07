//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.bukkit.enchantments;

import com.google.common.collect.Lists;
import java.util.Locale;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Translatable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.registry.RegistryAware;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Enchantment implements Keyed, Translatable, RegistryAware {
    public static final Enchantment PROTECTION = getEnchantment("protection");
    public static final Enchantment FIRE_PROTECTION = getEnchantment("fire_protection");
    public static final Enchantment FEATHER_FALLING = getEnchantment("feather_falling");
    public static final Enchantment BLAST_PROTECTION = getEnchantment("blast_protection");
    public static final Enchantment PROJECTILE_PROTECTION = getEnchantment("projectile_protection");
    public static final Enchantment RESPIRATION = getEnchantment("respiration");
    public static final Enchantment AQUA_AFFINITY = getEnchantment("aqua_affinity");
    public static final Enchantment THORNS = getEnchantment("thorns");
    public static final Enchantment DEPTH_STRIDER = getEnchantment("depth_strider");
    public static final Enchantment FROST_WALKER = getEnchantment("frost_walker");
    public static final Enchantment BINDING_CURSE = getEnchantment("binding_curse");
    public static final Enchantment SHARPNESS = getEnchantment("sharpness");
    public static final Enchantment SMITE = getEnchantment("smite");
    public static final Enchantment BANE_OF_ARTHROPODS = getEnchantment("bane_of_arthropods");
    public static final Enchantment KNOCKBACK = getEnchantment("knockback");
    public static final Enchantment FIRE_ASPECT = getEnchantment("fire_aspect");
    public static final Enchantment LOOTING = getEnchantment("looting");
    public static final Enchantment SWEEPING_EDGE = getEnchantment("sweeping_edge");
    public static final Enchantment EFFICIENCY = getEnchantment("efficiency");
    public static final Enchantment SILK_TOUCH = getEnchantment("silk_touch");
    public static final Enchantment UNBREAKING = getEnchantment("unbreaking");
    public static final Enchantment FORTUNE = getEnchantment("fortune");
    public static final Enchantment POWER = getEnchantment("power");
    public static final Enchantment PUNCH = getEnchantment("punch");
    public static final Enchantment FLAME = getEnchantment("flame");
    public static final Enchantment INFINITY = getEnchantment("infinity");
    public static final Enchantment LUCK_OF_THE_SEA = getEnchantment("luck_of_the_sea");
    public static final Enchantment LURE = getEnchantment("lure");
    public static final Enchantment LOYALTY = getEnchantment("loyalty");
    public static final Enchantment IMPALING = getEnchantment("impaling");
    public static final Enchantment RIPTIDE = getEnchantment("riptide");
    public static final Enchantment CHANNELING = getEnchantment("channeling");
    public static final Enchantment MULTISHOT = getEnchantment("multishot");
    public static final Enchantment QUICK_CHARGE = getEnchantment("quick_charge");
    public static final Enchantment PIERCING = getEnchantment("piercing");
    public static final Enchantment DENSITY = getEnchantment("density");
    public static final Enchantment BREACH = getEnchantment("breach");
    public static final Enchantment WIND_BURST = getEnchantment("wind_burst");
    public static final Enchantment MENDING = getEnchantment("mending");
    public static final Enchantment VANISHING_CURSE = getEnchantment("vanishing_curse");
    public static final Enchantment SOUL_SPEED = getEnchantment("soul_speed");
    public static final Enchantment SWIFT_SNEAK = getEnchantment("swift_sneak");

    public Enchantment() {
    }

    @NotNull
    private static Enchantment getEnchantment(@NotNull String key) {
        return (Enchantment)Registry.ENCHANTMENT.getOrThrow(NamespacedKey.minecraft(key));
    }

    /** @deprecated */
    @Deprecated(
            since = "1.13"
    )
    @NotNull
    public abstract String getName();

    public abstract int getMaxLevel();

    public abstract int getStartLevel();

    /** @deprecated */
    @Deprecated(
            since = "1.20.5"
    )
    @NotNull
    public abstract EnchantmentTarget getItemTarget();

    /** @deprecated */
    @Deprecated(
            since = "1.21"
    )
    public abstract boolean isTreasure();

    /** @deprecated */
    @Deprecated(
            since = "1.13"
    )
    public abstract boolean isCursed();

    public abstract boolean conflictsWith(@NotNull Enchantment var1);

    public abstract boolean canEnchantItem(@NotNull ItemStack var1);

    /** @deprecated */
    @Deprecated(
            since = "1.21.4"
    )
    @NotNull
    public abstract NamespacedKey getKey();

    /** @deprecated */
    @Deprecated(
            since = "1.20.3"
    )
    @Contract("null -> null")
    @Nullable
    public static Enchantment getByKey(@Nullable NamespacedKey key) {
        return key == null ? null : (Enchantment)Registry.ENCHANTMENT.get(key);
    }

    /** @deprecated */
    @Deprecated(
            since = "1.13"
    )
    @Contract("null -> null")
    @Nullable
    public static Enchantment getByName(@Nullable String name) {
        return name == null ? null : getByKey(NamespacedKey.fromString(name.toLowerCase(Locale.ROOT)));
    }

    /** @deprecated */
    @Deprecated(
            since = "1.20.3"
    )
    @NotNull
    public static Enchantment[] values() {
        return (Enchantment[])Lists.newArrayList(Registry.ENCHANTMENT).toArray(new Enchantment[0]);
    }
}
