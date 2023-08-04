package net.fabricmc.example;

import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.util.StringIdentifiable;

public class EnchantmentArgumentType extends EnumArgumentType<EnchantmentTargetAdapter> {
    protected EnchantmentArgumentType() {
        super(StringIdentifiable.createCodec(EnchantmentTargetAdapter::values), EnchantmentTargetAdapter::values);
    }
}
