package net.fabricmc.example.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Redirect(
            method = "hasOutline(Lnet/minecraft/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isGlowing()Z"))
    private boolean injectHasOutline(Entity entity) {
        return entity.isGlowing() || entity.isGlowingLocal();
    }
}
