package net.fabricmc.example;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class XrayRenders extends ConcurrentLinkedQueue<XrayRenderable> implements XrayRenderable {

    @Override
    public void render(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d cameraPos) {
        for (XrayRenderable item : this) {
            item.render(matrixStack, vertexConsumer, cameraPos);
        }
    }
}
