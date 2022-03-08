package net.fabricmc.example;

import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;

import java.util.List;

public interface EntitySelectorInterface {

    List<? extends Entity> getEntitiesClient(FabricClientCommandSource source);

    static EntitySelectorInterface of(EntitySelector entitySelector) {
        return (EntitySelectorInterface) (Object) entitySelector;
    }
}
