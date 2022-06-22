package net.fabricmc.example;


import net.minecraft.entity.projectile.FishingBobberEntity;

public interface Fishable {

    static Fishable of(FishingBobberEntity obj) {
        return (Fishable) obj;
    }

    boolean isCaughtFish();

}
