package net.fabricmc.example.mixin;

import net.fabricmc.example.KeyPressable;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(KeyBinding.class)
public class KeyBindingMixin implements KeyPressable {

    @Shadow
    private int timesPressed;

    @Override
    public void onKeyPressed() {
        ++timesPressed;
    }
}
