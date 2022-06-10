package net.fabricmc.example;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;

import java.util.List;

public interface EntitySelectorInterface {

    static EntitySelectorInterface of(EntitySelector entitySelector) {
        return (EntitySelectorInterface) entitySelector;
    }

    List<? extends Entity> getEntitiesClient(FabricClientCommandSource source);
}
