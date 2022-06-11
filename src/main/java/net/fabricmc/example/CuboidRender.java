package net.fabricmc.example;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class CuboidRender implements XrayRenderable {

    private VoxelShape shapes;
    private int color;

    public CuboidRender(VoxelShape shapes, int color) {
        this.shapes = shapes;
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
