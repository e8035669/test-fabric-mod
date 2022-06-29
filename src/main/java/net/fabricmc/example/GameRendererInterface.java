package net.fabricmc.example;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;

public interface GameRendererInterface {

    static GameRendererInterface of(GameRenderer obj) {
        return (GameRendererInterface) obj;
    }

    double getFov(float tickDelta);

}
