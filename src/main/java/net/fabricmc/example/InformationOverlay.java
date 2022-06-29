package net.fabricmc.example;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

import java.util.*;

public class InformationOverlay implements HudRenderCallback {

    private static final Identifier MY_TEXTURE = new Identifier("tutorial", "textures/information/circle.png");

    private static final Identifier COW_TEXTURE = new Identifier("tutorial", "textures/information/cow.png");
    private static final Identifier VILLAGER_TEXTURE = new Identifier("tutorial", "textures/information/villager.png");
    private static final Identifier PIG_TEXTURE = new Identifier("tutorial", "textures/information/pig.png");

    private static final Map<Class<?>, Identifier> SUPPORT_ENTITIES = ImmutableMap.of(
            CowEntity.class, COW_TEXTURE,
            VillagerEntity.class, VILLAGER_TEXTURE,
            PigEntity.class, PIG_TEXTURE
    );

    private static int TEXT_COLOR = 0x90FFFFFF;
    private EnumSet<DisplayMode> displayModes;
    private ShowMode showMode;

    public InformationOverlay() {
        setShowMode(ShowMode.MOTION);
    }

    public void setShowMode(ShowMode showMode) {
        this.showMode = showMode;
        switch (showMode) {
            case NONE -> displayModes = EnumSet.noneOf(DisplayMode.class);
            case FISH -> displayModes = EnumSet.of(DisplayMode.FISH);
            case MOTION -> displayModes = EnumSet.of(DisplayMode.MOTION);
        }
    }

    @Override
    public void onHudRender(MatrixStack matrixStack, float tickDelta) {
        if (displayModes.contains(DisplayMode.MOTION)) {
            drawMotionInfo(matrixStack, tickDelta);
        }
        if (displayModes.contains(DisplayMode.FISH)) {
            drawFishingInfo(matrixStack, tickDelta);
        }
        // drawEntityPositions(matrixStack, tickDelta);
        if (true) {
            drawEntityPositions2(matrixStack, tickDelta);
        }
    }

    public void drawMotionInfo(MatrixStack matrixStack, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        TextRenderer textRenderer = client.textRenderer;
        int height = client.getWindow().getScaledHeight();
        int width = client.getWindow().getScaledWidth();

        matrixStack.push();

        Vec3d velocity = player.getVelocity();
        BlockPos blockPos = player.getBlockPos();
        String speed = String.format("Spd: %.2f, %.2f, %.2f, (%.2f, %.2f, %.2f)", player.forwardSpeed,
                player.sidewaysSpeed, player.getMovementSpeed(), velocity.x, velocity.y, velocity.z);
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, speed, 0, height - 40, 0x90FFFFFF);
        String position = String.format("Pos: (%.2f, %.2f, %.2f)", player.getX(), player.getY(), player.getZ());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, position, 0, height - 30, 0x90FFFFFF);
        String blockPosStr = String.format("BPos: (%d, %d, %d)", blockPos.getX(), blockPos.getY(), blockPos.getZ());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, blockPosStr, 0, height - 20, 0x90FFFFFF);
        String dir = String.format("Dir: (%.2f, %.2f)", MathHelper.wrapDegrees(player.getYaw()), player.getPitch());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, dir, 0, height - 10, 0x90FFFFFF);

        matrixStack.pop();
    }

    public void drawFishingInfo(MatrixStack matrixStack, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        TextRenderer textRenderer = client.textRenderer;
        int height = client.getWindow().getScaledHeight();
        int width = client.getWindow().getScaledWidth();

        matrixStack.push();

        if (player.fishHook == null) {
            String fishHookMsg = "Not fishing.";
            DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, fishHookMsg, 0, height - 30, TEXT_COLOR);
        } else {
            FishingBobberEntity fishHook = player.fishHook;
            Fishable fishable = Fishable.of(fishHook);

            String fishHookMsg = String.format("Fishing: %s, ", fishable.getState());
            // String fishHookMsg = String.format("Fishing: ");
            if (fishable.isCaughtFish()) {
                fishHookMsg += "caught, ";
            }
            if (fishHook.isOnGround()) {
                fishHookMsg += "ground, ";
            }
            if (fishHook.getHookedEntity() != null) {
                fishHookMsg += String.format("hook %s", fishHook.getHookedEntity().getName().getString());
            }
            DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, fishHookMsg, 0, height - 30, TEXT_COLOR);
        }

        String time = String.format("Time: %d, %d",
                client.world.getTime(), client.world.getTimeOfDay());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, time, 0, height - 20, TEXT_COLOR);

        matrixStack.pop();
    }

    public void drawEntityPositions(MatrixStack matrixStack, float tickDelta) {
        matrixStack.push();

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
            BlockPos blockPos = hitResult.getBlockPos();

            // client.player.sendMessage(Text.literal("%s".formatted(blockPos)));

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            float aspect = (float) width / height;
            double fov = client.options.getFov().getValue();
            double angleSize = fov / height;

            Camera camera = client.gameRenderer.getCamera();

            Quaternion cameraDirection = camera.getRotation();
            cameraDirection.conjugate();
            Vec3d cameraPos = camera.getPos();

            Vec3f result = new Vec3f(cameraPos.subtract(Vec3d.ofCenter(blockPos, 1.0f)));
            result.transform(new Matrix3f(cameraDirection));

            float half_height = height / 2.0f;
            float scale_factor =
                    (float) (half_height / (result.getZ() * Math.tan(MathHelper.RADIANS_PER_DEGREE * fov / 2)));

            result.multiplyComponentwise(-scale_factor, -scale_factor, 1);

            // DrawableHelper.drawCenteredText(matrixStack, client.textRenderer, "X", (int) (result.getX() + width /
            // 2f),
            //         (int) (result.getY() + height / 2f), 0xFFFFFFFF);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, MY_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            DrawableHelper.drawTexture(matrixStack, (int) (result.getX() + width / 2f - 7),
                    (int) (result.getY() + height / 2f - 7), 0f, 0f, 15, 15, 15, 15);
            RenderSystem.disableBlend();


            // client.player.sendMessage(Text.literal("%s %f %f".formatted(cameraDirection, fov, angleSize)));
            // client.player.sendMessage(Text.literal("%d, %d, %f".formatted(width, height, aspect)));
            // client.player.sendMessage(Text.literal("%s".formatted(result)));

        }

        matrixStack.pop();
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

        for (Entity entity: client.world.getEntities()) {
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
                    DrawableHelper.drawTexture(matrixStack, (int)drawX, (int)drawY,
                            0f, 0f, textureSize, textureSize, textureSize, textureSize);
                }
            }
        }
        RenderSystem.disableBlend();

        matrixStack.pop();
    }

    private record PositionResult(float x, float y, boolean front) {}

    private static PositionResult projectToScreen(Vec3d pos, Vec3d cameraPos, Matrix3f cameraDirection, int height, double fov) {
        Vec3f result = new Vec3f(cameraPos.subtract(pos));
        result.transform(cameraDirection);

        float half_height = height / 2.0f;
        float scale_factor =
                (float) (half_height / (result.getZ() * Math.tan(MathHelper.RADIANS_PER_DEGREE * fov / 2)));

        result.multiplyComponentwise(-scale_factor, -scale_factor, 1);
        return new PositionResult(result.getX(), result.getY(), result.getZ() < 0);
    }

    public enum ShowMode implements StringIdentifiable {
        NONE,
        MOTION,
        FISH,
        ;

        @Override
        public String asString() {
            return switch (this) {
                case NONE -> "none";
                case MOTION -> "motion";
                case FISH -> "fish";
            };
        }
    }

    private enum DisplayMode {
        MOTION,
        FISH,
    }

}
