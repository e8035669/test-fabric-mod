package net.fabricmc.example.mixin;

import net.fabricmc.example.WorldRendererInterface;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin implements WorldRendererInterface {
    @Shadow
    private BufferBuilderStorage bufferBuilders;
    @Shadow
    private PostEffectProcessor entityOutlinePostProcessor;
    @Shadow
    private MinecraftClient client;
    @Shadow
    private ClientWorld world;

    private boolean isForceOutline;

    @Shadow
    public static void drawShapeOutline(MatrixStack matrices, VertexConsumer vertexConsumer, VoxelShape shape,
                                        double offsetX, double offsetY, double offsetZ, float red, float green, float blue, float alpha, boolean bl) {
    }

    @Override
    public BufferBuilderStorage getBufferBuilders() {
        return bufferBuilders;
    }

    @Inject(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/OutlineVertexConsumerProvider;draw()V",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectOutlineShader(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo ci, Profiler profiler, Vec3d vec3d, double d, double e, double f, Matrix4f matrix4f, boolean bl, Frustum frustum, float g, boolean bl2, boolean bl3, VertexConsumerProvider.Immediate immediate) {
        if (!bl3 && isForceOutline) {
            this.entityOutlinePostProcessor.render(tickDelta);
            this.client.getFramebuffer().beginWrite(false);
        }
        isForceOutline = false;
        /*
        for (Entity entity : world.getEntities()) {
            entity.setGlowing(false);
        }

         */
    }

    @Override
    public boolean getForceOutline() {
        return isForceOutline;
    }

    @Override
    public void setForceOutline(boolean value) {
        isForceOutline = value;
    }

    public void drawBlockOutline2(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, double d,
                                  double e, double f, BlockPos pos, BlockState state) {
        drawShapeOutline(matrices, vertexConsumer, state.getOutlineShape(this.world, pos, ShapeContext.of(entity)),
                (double) pos.getX() - d, (double) pos.getY() - e, (double) pos.getZ() - f,
                0.0f, 0.0f, 0.0f, 0.4f, false);
    }

}
