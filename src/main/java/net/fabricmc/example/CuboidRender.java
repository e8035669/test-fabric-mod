package net.fabricmc.example;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class CuboidRender implements XrayRenderable {

    private VoxelShape shapes;
    private int color;

    public CuboidRender(VoxelShape shapes, int color) {
        this.shapes = shapes;
        this.color = color;
    }

    public CuboidRender(BlockPos blockPos, int color) {
        this.shapes = VoxelShapes.fullCube().offset(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        this.color = color;
    }

    @Override
    public void render(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d cameraPos) {
        renderCuboid(matrixStack, vertexConsumer, cameraPos);
    }

    private void renderCuboid(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d cameraPos) {
        shapes.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) -> {
            DrawHelper.drawLine(new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ),
                    color, cameraPos, vertexConsumer, matrixStack);
        });
    }
}
