package net.fabricmc.example;

import com.mojang.serialization.Codec;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.util.StringIdentifiable;

import java.util.function.Supplier;

public class EnchantmentArgumentType extends EnumArgumentType<EnchantmentTargetAdapter> {
    protected EnchantmentArgumentType() {
        super(StringIdentifiable.createCodec(EnchantmentTargetAdapter::values), EnchantmentTargetAdapter::values);
    }
}
