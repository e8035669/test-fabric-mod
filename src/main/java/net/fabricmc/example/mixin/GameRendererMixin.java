package net.fabricmc.example.mixin;

import net.fabricmc.example.GameRendererInterface;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GameRenderer.class)
public class GameRendererMixin implements GameRendererInterface {

    @Shadow
    private double getFov(Camera camera, float tickDelta, boolean changingFov) {
        return 0;
    }

    @Shadow
    @Final
    private Camera camera;

    @Override
    public double getFov(float tickDelta) {
        return getFov(this.camera, tickDelta, true);
    }
}
