package net.fabricmc.example;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

public class XrayRender implements WorldRenderEvents.End {

    public List<XrayRenderable> getRenderables() {
        return renderables;
    }

    private final List<XrayRenderable> renderables = new ArrayList<>();

    @Override
    public void onEnd(WorldRenderContext context) {
        MatrixStack matrixStack = context.matrixStack();
        matrixStack.push();
        Camera camera = context.camera();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder vertexConsumer = tessellator.getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
        RenderSystem.disableTexture();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        // RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);

        MatrixStack matrixStack1 = RenderSystem.getModelViewStack();
        matrixStack1.push();
        matrixStack1.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.lineWidth(3.0f);

        vertexConsumer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);


        CuboidRender cuboidRender = new CuboidRender(VoxelShapes.fullCube().offset(4, 2, 2), 0xFF00FFFF);
        cuboidRender.render(matrixStack, vertexConsumer, camera.getPos());

        for (XrayRenderable renderable : renderables) {
            renderable.render(matrixStack, vertexConsumer, camera.getPos());
        }

        tessellator.draw();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        matrixStack1.pop();
        RenderSystem.applyModelViewMatrix();

        matrixStack.pop();
    }

    public void add(XrayRenderable renderable) {
        if (renderable != null && !renderables.contains(renderable)) {
            renderables.add(renderable);
        }
    }

    public void remove(XrayRenderable renderable) {
        if (renderable != null) {
            renderables.remove(renderable);
        }
    }



}
