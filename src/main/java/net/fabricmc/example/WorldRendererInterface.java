package net.fabricmc.example;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

public interface WorldRendererInterface {

    BufferBuilderStorage getBufferBuilders();

    void setForceOutline(boolean value);

    boolean getForceOutline();

    static WorldRendererInterface of(WorldRenderer obj) {
        return (WorldRendererInterface) obj;
    }

    void drawBlockOutline2(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, double d,
                                  double e, double f, BlockPos pos, BlockState state);

}
