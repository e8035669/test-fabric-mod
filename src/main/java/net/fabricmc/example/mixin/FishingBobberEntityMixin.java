package net.fabricmc.example.mixin;

import net.fabricmc.example.Fishable;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin implements Fishable {

    @Shadow
    private boolean caughtFish;

    @Shadow
    private FishingBobberEntity.State state;

    @Override
    public boolean isCaughtFish() {
        return caughtFish;
    }

    @Override
    public FishingBobberEntity.State getState() {
        return state;
    }

}
