package net.fabricmc.example;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

import java.util.Map;
import java.util.Objects;

public class EntityIndicateOverlay implements HudRenderCallback {

    private static final Identifier MY_TEXTURE = new Identifier("tutorial", "textures/information/circle.png");

    private static final Identifier COW_TEXTURE = new Identifier("tutorial", "textures/information/cow.png");
    private static final Identifier VILLAGER_TEXTURE = new Identifier("tutorial", "textures/information/villager.png");
    private static final Identifier PIG_TEXTURE = new Identifier("tutorial", "textures/information/pig.png");

    private static final Map<Class<?>, Identifier> SUPPORT_ENTITIES = ImmutableMap.of(
            CowEntity.class, COW_TEXTURE,
            VillagerEntity.class, VILLAGER_TEXTURE,
            PigEntity.class, PIG_TEXTURE
    );

    private boolean enable = true;

    private static PositionResult projectToScreen(Vec3d pos, Vec3d cameraPos, Matrix3f cameraDirection, int height, double fov) {
        Vec3f result = new Vec3f(cameraPos.subtract(pos));
        result.transform(cameraDirection);

        float half_height = height / 2.0f;
        float scale_factor =
                (float) (half_height / (result.getZ() * Math.tan(MathHelper.RADIANS_PER_DEGREE * fov / 2)));

        result.multiplyComponentwise(-scale_factor, -scale_factor, 1);
        return new PositionResult(result.getX(), result.getY(), result.getZ() < 0);
    }

    @Override
    public void onHudRender(MatrixStack matrixStack, float tickDelta) {
        if (enable) {
            drawEntityPositions2(matrixStack, tickDelta);
        }
    }

    public boolean getEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void drawEntityPositions2(MatrixStack matrixStack, float tickDelta) {
        matrixStack.push();

        MinecraftClient client = MinecraftClient.getInstance();

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        double fov = GameRendererInterface.of(client.gameRenderer).getFov(tickDelta);

        Camera camera = client.gameRenderer.getCamera();

        Quaternion cameraDirection1 = camera.getRotation();
        cameraDirection1.conjugate();
        Matrix3f cameraDirection = new Matrix3f(cameraDirection1);
        Vec3d cameraPos = camera.getPos();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.8f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int textureSize = 16;
        int border = textureSize / 2;

        for (Entity entity : client.world.getEntities()) {
            Identifier entityImage = SUPPORT_ENTITIES.get(entity.getClass());
            if (entity.getSyncedPos().distanceTo(cameraPos) < 60 && Objects.nonNull(entityImage)) {
                RenderSystem.setShaderTexture(0, entityImage);
                PositionResult positionResult = projectToScreen(entity.getEyePos().add(0, 0.5, 0), cameraPos,
                        cameraDirection, height, fov);
                if (positionResult.front) {
                    int drawX = MathHelper.clamp((int) (positionResult.x + halfWidth - border), 0, width - textureSize);
                    int drawY = MathHelper.clamp((int) (positionResult.y + halfHeight - border), 0, height - textureSize);

                    DrawableHelper.drawTexture(matrixStack, drawX, drawY,
                            0f, 0f, textureSize, textureSize, textureSize, textureSize);
                } else {
                    double drawX = -positionResult.x;
                    double drawY = -positionResult.y;

                    double minDistance = Math.max(
                            Math.min(halfWidth - border - Math.abs(drawX), halfHeight - border - Math.abs(drawY)
                            ), 0);
                    if (drawX > 0) {
                        drawX = Math.min(drawX + minDistance, halfWidth - border);
                    } else {
                        drawX = Math.max(drawX - minDistance, -(halfWidth - border));
                    }

                    if (drawY > 0) {
                        drawY = Math.min(drawY + minDistance, halfHeight - border);
                    } else {
                        drawY = Math.max(drawY - minDistance, -(halfHeight - border));
                    }
                    drawX += (halfWidth - border);
                    drawY += (halfHeight - border);
                    DrawableHelper.drawTexture(matrixStack, (int) drawX, (int) drawY,
                            0f, 0f, textureSize, textureSize, textureSize, textureSize);
                }
            }
        }
        RenderSystem.disableBlend();

        matrixStack.pop();
    }

    private record PositionResult(float x, float y, boolean front) {
    }

}
