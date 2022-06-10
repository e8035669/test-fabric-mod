package net.fabricmc.example;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class OutlineEntitiesTask implements Renderable, Runnable {
    public static final Logger LOGGER = LogManager.getLogger("OutlineBlocksTask");

    private final EntitySelector entitySelector;
    private final FabricClientCommandSource commandSource;
    private MinecraftClient client;
    private List<? extends Entity> lastOutlineEntities;

    public OutlineEntitiesTask(EntitySelector entitySelector, FabricClientCommandSource commandSource) {
        this.entitySelector = entitySelector;
        this.commandSource = commandSource;
        this.lastOutlineEntities = List.of();
    }

    public void setClient(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        EntitySelectorInterface entitySelectorInterface = EntitySelectorInterface.of(entitySelector);
        List<? extends Entity> entities = entitySelectorInterface.getEntitiesClient(commandSource);
        entities.remove(client.player);
        lastOutlineEntities = entities;
        LOGGER.info("Current outline count: %d".formatted(lastOutlineEntities.size()));
    }

    @Override
    public void onRendering(WorldRenderContext wrc) {
        WorldRendererInterface worldRendererInterface = WorldRendererInterface.of(wrc.worldRenderer());

        if (lastOutlineEntities.size() > 0) {
            for (Entity entity : lastOutlineEntities) {
                entity.setGlowing(true);
            }
            worldRendererInterface.setForceOutline(true);
        }
    }
}
