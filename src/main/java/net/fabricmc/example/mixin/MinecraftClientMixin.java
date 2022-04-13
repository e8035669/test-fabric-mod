package net.fabricmc.example.mixin;

import net.fabricmc.example.FakeFocusable;
import net.fabricmc.example.HotPlugMouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(MinecraftClient.class)
public class MinecraftClientMixin implements FakeFocusable {

    private boolean isFakeFocus = false;

    @Redirect(
            method = "hasOutline(Lnet/minecraft/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isGlowing()Z"))
    private boolean injectHasOutline(Entity entity) {
        return entity.isGlowing() || entity.isGlowingLocal();
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "net/minecraft/client/Mouse"))
    private Mouse redirectHotPlugMouse(MinecraftClient client) {
        return new HotPlugMouse(client);
    }

    @Inject(method = "isWindowFocused()Z", at = @At("RETURN"), cancellable = true)
    private void injectWindowFocus(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cir.getReturnValueZ() || this.isFakeFocus);
    }

    @Override
    public void setFakeFocus(boolean isFakeFocus) {
        this.isFakeFocus = isFakeFocus;
    }
}
