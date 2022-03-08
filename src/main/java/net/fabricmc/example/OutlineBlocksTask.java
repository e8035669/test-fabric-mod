package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class OutlineBlocksTask implements Renderable, Runnable {
    public static final Logger LOGGER = LogManager.getLogger("OutlineBlocksTask");

    private final Block targetBlock;
    private final int scanRange;
    private MinecraftClient client;

    private List<BlockPos> lastFindBlocks;

    public OutlineBlocksTask(Block targetBlock, int scanRange) {
        this.targetBlock = targetBlock;
        this.scanRange = scanRange;
    }

    public void setClient(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        if (client == null || client.player == null || client.world == null) return;

        BlockPos playerPos = client.player.getBlockPos();

        List<BlockPos> blockPosList = BlockPos.streamOutwards(playerPos, this.scanRange, this.scanRange, this.scanRange)
                .filter(blockPos -> {
                    BlockState blockState = client.world.getBlockState(blockPos);
                    if (blockState.isAir()) return false;
                    return blockState.getBlock().equals(this.targetBlock);
                })
                .map(BlockPos::toImmutable)
                .toList();
        lastFindBlocks = blockPosList;
        LOGGER.info("Find %s: %d".formatted(this.targetBlock, blockPosList.size()));
    }

    @Override
    public void onRendering(WorldRenderContext wrc) {
        if (lastFindBlocks == null || lastFindBlocks.size() == 0) return;

        Vec3d cameraPos = wrc.camera().getPos();
        WorldRendererInterface worldRendererInterface = WorldRendererInterface.of(wrc.worldRenderer());
        OutlineVertexConsumerProvider outlineVertexConsumerProvider =
                worldRendererInterface.getBufferBuilders().getOutlineVertexConsumers();
        worldRendererInterface.setForceOutline(true);

        for (BlockPos blockPos : lastFindBlocks) {
            if (cameraPos.distanceTo(Vec3d.ofCenter(blockPos)) < 3) continue;
            double x = blockPos.getX() - cameraPos.x;
            double y = blockPos.getY() - cameraPos.y;
            double z = blockPos.getZ() - cameraPos.z;

            wrc.matrixStack().push();
            wrc.matrixStack().translate(x, y, z);
            this.client.getBlockRenderManager().renderBlockAsEntity(
                    this.targetBlock.getDefaultState(),
                    wrc.matrixStack(), outlineVertexConsumerProvider, 15728880, OverlayTexture.DEFAULT_UV);
            wrc.matrixStack().pop();
        }
    }
}
