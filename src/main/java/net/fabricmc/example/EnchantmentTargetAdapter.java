package net.fabricmc.example;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.util.StringIdentifiable;

public enum EnchantmentTargetAdapter implements StringIdentifiable {
    ARMOR(EnchantmentTarget.ARMOR),
    ARMOR_FEET(EnchantmentTarget.ARMOR_FEET),
    ARMOR_LEGS(EnchantmentTarget.ARMOR_LEGS),
    ARMOR_CHEST(EnchantmentTarget.ARMOR_CHEST),
    ARMOR_HEAD(EnchantmentTarget.ARMOR_HEAD),
    WEAPON(EnchantmentTarget.WEAPON),
    DIGGER(EnchantmentTarget.DIGGER),
    FISHING_ROD(EnchantmentTarget.FISHING_ROD),
    TRIDENT(EnchantmentTarget.TRIDENT),
    BREAKABLE(EnchantmentTarget.BREAKABLE),
    BOW(EnchantmentTarget.BOW),
    WEARABLE(EnchantmentTarget.WEARABLE),
    CROSSBOW(EnchantmentTarget.CROSSBOW),
    VANISHABLE(EnchantmentTarget.VANISHABLE),
    ;

    public EnchantmentTarget target;

    EnchantmentTargetAdapter(EnchantmentTarget target) {
        this.target = target;
    }

    @Override
    public String asString() {
        return this.name();
    }
}
