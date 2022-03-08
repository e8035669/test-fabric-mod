package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public interface Renderable {
    void onRendering(WorldRenderContext wrc);
}
