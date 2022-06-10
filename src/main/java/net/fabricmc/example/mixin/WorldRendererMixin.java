package net.fabricmc.example.mixin;

import net.fabricmc.example.WorldRendererInterface;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.shape.VoxelShape;
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
    private ShaderEffect entityOutlineShader;
    @Shadow
    private MinecraftClient client;
    @Shadow
    private ClientWorld world;

    private boolean isForceOutline;

    @Shadow
    private static void drawShapeOutline(MatrixStack matrices, VertexConsumer vertexConsumer, VoxelShape voxelShape,
                                         double d, double e, double f, float g, float h, float i, float j) {
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
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void injectOutlineShader(MatrixStack matrices, float tickDelta, long arg2, boolean renderBlockOutline,
                                     Camera camera, GameRenderer gameRenderer,
                                     LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix,
                                     CallbackInfo ci, Profiler profiler, boolean bl, Vec3d vec3d, double d, double e,
                                     double f, Matrix4f matrix4f, boolean bl2, Frustum frustum, float g, boolean bl3,
                                     boolean bl4, VertexConsumerProvider.Immediate immediate) {
        if (!bl4 && isForceOutline) {
            this.entityOutlineShader.render(tickDelta);
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
                0.0f, 0.0f, 0.0f, 0.4f);
    }

}
