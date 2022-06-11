package net.fabricmc.example;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WalkPathRender implements XrayRenderable {

    private WalkPath path;
    private int color;

    public WalkPathRender(WalkPath path, int color) {
        this.path = path;
        this.color = color;
    }

    @Override
    public void render(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d cameraPos) {
        drawWalkPath(matrixStack, vertexConsumer, cameraPos);
    }

    private void drawWalkPath(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d cameraPos) {
        for (int i = 0; i < path.size() - 1; ++i) {
            BlockPos pos1 = path.get(i);
            BlockPos pos2 = path.get(i + 1);
            if (pos1.getY() - pos2.getY() == 0) {
                Vec3d vec1 = Vec3d.ofBottomCenter(pos1);
                Vec3d vec2 = Vec3d.ofBottomCenter(pos2);
                DrawHelper.drawLine(vec1, vec2, color, cameraPos, vertexConsumer, matrixStack);
            } else {
                Vec3d vec1 = Vec3d.ofBottomCenter(pos1);
                Vec3d vec2 = Vec3d.ofBottomCenter(pos2);
                Vec3d vec3 = new Vec3d((vec1.getX() + vec2.getX()) / 2, vec1.y, (vec1.getZ() + vec2.getZ()) / 2);
                Vec3d vec4 = new Vec3d((vec1.getX() + vec2.getX()) / 2, vec2.y, (vec1.getZ() + vec2.getZ()) / 2);
                DrawHelper.drawLine(vec1, vec3, color, cameraPos, vertexConsumer, matrixStack);
                DrawHelper.drawLine(vec3, vec4, color, cameraPos, vertexConsumer, matrixStack);
                DrawHelper.drawLine(vec4, vec2, color, cameraPos, vertexConsumer, matrixStack);
            }
        }
    }
}
