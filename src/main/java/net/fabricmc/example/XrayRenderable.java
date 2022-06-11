package net.fabricmc.example;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public interface XrayRenderable {

    void render(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d cameraPos);

}
