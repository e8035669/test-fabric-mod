package net.fabricmc.example;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;

public class DrawHelper {
    public static void drawLine(Vec3d pos1, Vec3d pos2, int color, Vec3d cameraPos, BufferBuilder vertexConsumer,
                         MatrixStack matrixStack) {
        MatrixStack.Entry entry = matrixStack.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        Vec3f diff = new Vec3f(pos2.subtract(pos1));
        float t =
                MathHelper.sqrt(diff.getX() * diff.getX() + diff.getY() * diff.getY() + diff.getZ() * diff.getZ());

        float a = (float) (pos1.x - cameraPos.x);
        float b = (float) (pos1.y - cameraPos.y);
        float c = (float) (pos1.z - cameraPos.z);

        vertexConsumer.vertex(positionMatrix, a, b, c).color(color).normal(normalMatrix, diff.getX() / t, diff.getY() / t,
                diff.getZ() / t).next();

        float d = (float) (pos2.x - cameraPos.x);
        float e = (float) (pos2.y - cameraPos.y);
        float f = (float) (pos2.z - cameraPos.z);

        vertexConsumer.vertex(positionMatrix, d, e, f).color(color).normal(normalMatrix, diff.getX() / t, diff.getY() / t,
                diff.getZ() / t).next();
    }
}