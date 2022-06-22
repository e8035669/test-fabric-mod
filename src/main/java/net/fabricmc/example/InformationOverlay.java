package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class InformationOverlay implements HudRenderCallback {

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
            DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, fishHookMsg, 0, height - 10, TEXT_COLOR);
        } else {
            FishingBobberEntity fishHook = player.fishHook;
            Fishable fishable = Fishable.of(fishHook);

            String fishHookMsg = "Fishing: ";
            if (fishable.isCaughtFish()) {
                fishHookMsg += "caught fish, ";
            }
            if (fishHook.isOnGround()) {
                fishHookMsg += "on ground, ";
            }
            if (fishHook.isInOpenWater()) {
                fishHookMsg += "in open water, ";
            }
            if (fishHook.getHookedEntity() != null) {
                fishHookMsg += String.format("hook %s", fishHook.getHookedEntity().getName().getString());
            }
            DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, fishHookMsg, 0, height - 10, TEXT_COLOR);
        }

        matrixStack.pop();
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
