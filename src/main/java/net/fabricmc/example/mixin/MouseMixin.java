package net.fabricmc.example.mixin;

import net.fabricmc.example.MouseInterface;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Mouse.class)
public abstract class MouseMixin implements MouseInterface {


    @Shadow
    public void onCursorPos(long window, double x, double y) {

    }

    @Shadow
    public void onMouseButton(long window, int button, int action, int mods) {

    }

    @Shadow
    public void onMouseScroll(long window, double horizontal, double vertical) {

    }

}
