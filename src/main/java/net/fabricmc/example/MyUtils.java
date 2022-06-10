package net.fabricmc.example;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MyUtils {
    public static final Logger LOGGER = LogManager.getLogger("MyUtils");

    public static void showTargetBlockInfo(MinecraftClient client) {
        HitResult hitResult = client.crosshairTarget;
        String hitResultStr = "";
        switch (hitResult.getType()) {
            case MISS -> hitResultStr = "Miss";
            case BLOCK -> {
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos blockPos = blockHitResult.getBlockPos();
                BlockState blockState =
                        client.world.getBlockState(blockPos);

                hitResultStr = String.format("%s @ (%d, %d, %d)"
                        , blockState, blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }
            case ENTITY -> {
                EntityHitResult entityHitResult =
                        (EntityHitResult) hitResult;
                hitResultStr = String.format("%s",
                        entityHitResult.getEntity());
            }
        }

        String message = String.format("Pos: (%.2f, %.2f, %.2f)," +
                        " " +
                        "(%.2f, %.2f), (%d, %d, %d), (%s)",
                client.player.getX(), client.player.getY(),
                client.player.getZ(),
                client.player.getYaw(), client.player.getPitch(),
                client.player.getBlockX(), client.player.getBlockY(),
                client.player.getBlockZ(), hitResultStr);


        client.player.sendMessage(Text.of(message), false);
    }

    public static void findNearestBlock(MinecraftClient client) {
        ItemStack itemInHand = client.player.getInventory().getMainHandStack();
        Item item = itemInHand.getItem();
        if (item instanceof BlockItem blockItem) {
            Block blockInHand = blockItem.getBlock();
            String message = String.format("%s is Block", blockInHand.toString());
            client.player.sendMessage(Text.of(message), false);

            BlockPos playerPos = client.player.getBlockPos();

            Optional<BlockPos> foundBlock =
                    BlockPos.streamOutwards(playerPos, 32, 32, 32)
                            .filter((blockPos -> {
                                BlockState blockState =
                                        client.world.getBlockState(blockPos);
                                return blockState.getBlock().equals(blockInHand);
                            }))
                            .map(BlockPos::toImmutable)
                            .findFirst();

            foundBlock.ifPresent(blockPos -> {
                LOGGER.info("Find block at %s".formatted(blockPos));
                Vec3d eyePos = client.player.getEyePos();
                Vec3d offset = Vec3d.of(blockPos).subtract(eyePos);

                LOGGER.info("Distance %s".formatted(offset));

                //Blocks.BEE_NEST.
                client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.ofCenter(blockPos));
            });


        } else {
            String message = String.format("%s is not a BlockItem",
                    item.toString());
            client.player.sendMessage(Text.of(message), false);
        }

    }

    public static void printContext(Object o) {
        LOGGER.info(o);
    }

    public static Optional<BlockPos> findBlockInRange(MinecraftClient client,
                                                      BlockState targetBlockState,
                                                      int range) {
        Block targetBlock = targetBlockState.getBlock();
        BlockPos playerPos = client.player.getBlockPos();

        Optional<BlockPos> foundBlock = BlockPos
                .streamOutwards(playerPos, range, range, range)
                .filter((blockPos -> {
                    BlockState blockState = client.world.getBlockState(blockPos);
                    return blockState.getBlock().equals(targetBlock);
                }))
                .map(BlockPos::toImmutable)
                .findFirst();

        foundBlock.ifPresent((blockPos) -> {
            BlockState blockState = client.world.getBlockState(blockPos);
            LOGGER.info("%s".formatted(blockState.getRaycastShape(client.world, blockPos).toString()));
            LOGGER.info("%s".formatted(blockState.getOutlineShape(client.world, blockPos).toString()));
            LOGGER.info("%s".formatted(blockState.getCollisionShape(client.world, blockPos).toString()));
            LOGGER.info("%s".formatted(blockState.getSidesShape(client.world, blockPos).toString()));

            TntEntity entity = new TntEntity(client.world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), null);
            entity.setId(-1);
            entity.setGlowing(true);
            entity.setVelocity(0, 0, 0);
            client.world.addEntity(-1, entity);
        });

        client.world.getEntities().forEach(entity -> {
            LOGGER.info("Entity %d %s".formatted(entity.getId(), entity.toString()));
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setGlowing(true);
            }
        });
        return foundBlock;
    }

    public static int executeFindBlock(CommandContext<FabricClientCommandSource> context) {
        BlockStateArgument blockState = context.getArgument("target", BlockStateArgument.class);
        int range = IntegerArgumentType.getInteger(context, "range");
        LOGGER.info(("Get argument %s %d").formatted(blockState.getBlockState(), range));

        ExampleClientMod.EXECUTOR.execute(() -> {
            LOGGER.info("Start finding block");
            Optional<BlockPos> foundBlock =
                    findBlockInRange(context.getSource().getClient(),
                            blockState.getBlockState(), range);


            if (foundBlock.isPresent()) {
                context.getSource().sendFeedback(
                        Text.of("Find %s at %s, Distance %.1f".formatted(
                                blockState.getBlockState().getBlock().toString(),
                                foundBlock.get().toString(),
                                context.getSource().getPlayer().getPos().distanceTo(Vec3d.ofCenter(foundBlock.get())))));
            } else {
                context.getSource().sendFeedback(
                        Text.of("Block %s not found".formatted(blockState.getBlockState().getBlock().toString()))
                );
            }
        });

        return 0;
    }

    public static void registerMyCommands() {
        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> {
            dispatcher.register(literal("findBlock")
                    .then(argument("target", BlockStateArgumentType.blockState(registryAccess))
                            .then(argument("range", IntegerArgumentType.integer(1, 999))
                                    .executes(MyUtils::executeFindBlock)
                            )
                    )
            );
        }));
    }


    public static void onAfterEntity(WorldRenderContext wrc) {

        WorldRendererInterface worldRendererInterface = WorldRendererInterface.of(wrc.worldRenderer());
        OutlineVertexConsumerProvider outlineVertexConsumerProvider =
                worldRendererInterface.getBufferBuilders().getOutlineVertexConsumers();
        worldRendererInterface.setForceOutline(true);

        Iterable<Entity> it = wrc.world().getEntities();
        for (Entity entity : it) {
            wrc.matrixStack().push();
            if (entity instanceof LivingEntity livingEntity && !(entity instanceof PlayerEntity)) {
                Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
                // Vec3d pos = livingEntity.getPos();
                double x = livingEntity.lastRenderX - cameraPos.x;
                double y = livingEntity.lastRenderY - cameraPos.y;
                double z = livingEntity.lastRenderZ - cameraPos.z;
                wrc.matrixStack().translate(x, y + 0.25 + 1, z);
                wrc.matrixStack().scale(0.5f, 0.5f, 0.5f);

                MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                        Blocks.DIAMOND_BLOCK.getDefaultState(),
                        wrc.matrixStack(), outlineVertexConsumerProvider, 15728880, OverlayTexture.DEFAULT_UV);

                // VertexConsumer vertexConsumer = outlineVertexConsumerProvider.getBuffer(RenderLayer.getLines());
                // vertexConsumer.vertex(wrc.projectionMatrix(), 0, 0, 0).color(0, 0, 0, 255).next();
                // vertexConsumer.vertex(wrc.projectionMatrix(), (float)x, (float)y, (float)z).color(0, 0, 0, 255)
                // .next();
            }
            wrc.matrixStack().pop();
        }

        HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos blockPos = ((BlockHitResult) hitResult).getBlockPos();
            BlockState blockState = wrc.world().getBlockState(blockPos);
            if (!blockState.isAir() && wrc.world().getWorldBorder().contains(blockPos)) {
                VertexConsumer vertexConsumer =
                        outlineVertexConsumerProvider.getBuffer(RenderLayers.getEntityBlockLayer(blockState, false));

                Vec3d vec3d = wrc.camera().getPos();
                double d = vec3d.getX();
                double e = vec3d.getY();
                double f = vec3d.getZ();

                worldRendererInterface.drawBlockOutline2(wrc.matrixStack(), vertexConsumer,
                        wrc.camera().getFocusedEntity(), d, e, f, blockPos, blockState);

            }
        }
    }
}
