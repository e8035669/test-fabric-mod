package net.fabricmc.example.mixin;

import net.fabricmc.example.ClientPlayerInteractionManagerHelper;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void onClickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player,
                             CallbackInfo ci) {
        ClientPlayerInteractionManagerHelper.LOGGER.info("onClick(%d, %d, %d, %s, %s)".formatted(syncId, slotId,
                button, actionType, player));
    }

}
