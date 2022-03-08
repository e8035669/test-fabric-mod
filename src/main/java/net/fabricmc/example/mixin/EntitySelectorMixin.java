package net.fabricmc.example.mixin;

import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.example.EntitySelectorInterface;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin implements EntitySelectorInterface {

    @Shadow
    private String playerName;
    @Shadow
    private UUID uuid;
    @Shadow
    private Function<Vec3d, Vec3d> positionOffset;
    @Shadow
    private boolean includesNonPlayers;
    @Shadow
    private boolean senderOnly;
    @Shadow
    private Box box;
    @Shadow
    private TypeFilter<Entity, ?> entityFilter;
    @Shadow
    private Predicate<Entity> getPositionPredicate(Vec3d pos) { return null; }
    @Shadow
    public boolean isLocalWorldOnly() { return false; }
    @Shadow
    private <T extends Entity> List<T> getEntities(Vec3d pos, List<T> entities) {return null;}

    @Override
    public List<? extends Entity> getEntitiesClient(FabricClientCommandSource source) {
        if (!this.includesNonPlayers) {
            return this.getPlayersClient(source);
        }
        if (this.playerName != null) {
            return source.getWorld().getPlayers()
                    .stream().filter(
                            player -> player.getGameProfile().getName().equalsIgnoreCase(this.playerName)
                    ).toList();
        }
        if (this.uuid != null) {
            return source.getWorld().getPlayers().stream().filter(
                    player -> player.getUuid().equals(this.uuid)
            ).toList();
        }
        Vec3d vec3d = this.positionOffset.apply(source.getPosition());
        Predicate<Entity> predicate = this.getPositionPredicate(vec3d);
        if (this.senderOnly) {
            if (source.getEntity() != null && predicate.test(source.getEntity())) {
                return Lists.newArrayList(source.getEntity());
            }
            return Collections.emptyList();
        }
        ArrayList<Entity> list = Lists.newArrayList();
        this.appendEntitiesFromWorld2(list, source.getWorld(), vec3d, predicate);
        return this.getEntities(vec3d, list);
    }

    private void appendEntitiesFromWorld2(List<Entity> result, ClientWorld world, Vec3d pos,
                                          Predicate<Entity> predicate) {
        if (this.box != null) {
            result.addAll(world.getEntitiesByType(this.entityFilter, this.box.offset(pos), predicate));
        } else {
            for (Entity entity : world.getEntities()) {
                Entity entity2 = entityFilter.downcast(entity);
                if (entity2 == null) continue;
                if (predicate.test(entity2)) {
                    result.add(entity2);
                }
            }
        }
    }

    private List<AbstractClientPlayerEntity> getPlayersClient(FabricClientCommandSource source) {
        List<AbstractClientPlayerEntity> list;
        if (this.playerName != null) {
            return source.getWorld().getPlayers()
                    .stream().filter(
                            player -> player.getGameProfile().getName().equalsIgnoreCase(this.playerName)
                    ).toList();
        }
        if (this.uuid != null) {
            return source.getWorld().getPlayers().stream().filter(
                    player -> player.getUuid().equals(this.uuid)
            ).toList();
        }
        Vec3d vec3d = this.positionOffset.apply(source.getPosition());
        Predicate<Entity> predicate = this.getPositionPredicate(vec3d);
        if (this.senderOnly) {
            if (source.getEntity() instanceof AbstractClientPlayerEntity && predicate.test(source.getEntity())) {
                return Lists.newArrayList((AbstractClientPlayerEntity)source.getEntity());
            }
            return Collections.emptyList();
        }
        list = source.getWorld().getPlayers().stream().filter(
                predicate::test
        ).toList();

        return this.getEntities(vec3d, list);
    }

}
