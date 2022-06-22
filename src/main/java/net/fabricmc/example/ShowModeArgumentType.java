package net.fabricmc.example;

import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.util.StringIdentifiable;


public class ShowModeArgumentType extends EnumArgumentType<InformationOverlay.ShowMode> {
    protected ShowModeArgumentType() {
        super(StringIdentifiable.createCodec(InformationOverlay.ShowMode::values), InformationOverlay.ShowMode::values);
    }
}
