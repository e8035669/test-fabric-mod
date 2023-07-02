package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.FishingBobberEntity;
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
        setShowMode(ShowMode.NONE);
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
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        if (displayModes.contains(DisplayMode.MOTION)) {
            drawMotionInfo(drawContext, tickDelta);
        }
        if (displayModes.contains(DisplayMode.FISH)) {
            drawFishingInfo(drawContext, tickDelta);
        }

    }

    public void drawMotionInfo(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        TextRenderer textRenderer = client.textRenderer;
        int height = client.getWindow().getScaledHeight();
        int width = client.getWindow().getScaledWidth();

        MatrixStack matrixStack = drawContext.getMatrices();
        matrixStack.push();

        Vec3d velocity = player.getVelocity();
        BlockPos blockPos = player.getBlockPos();
        String speed = String.format("Spd: %.2f, %.2f, %.2f, (%.2f, %.2f, %.2f)", player.forwardSpeed,
                player.sidewaysSpeed, player.getMovementSpeed(), velocity.x, velocity.y, velocity.z);
        drawContext.drawTextWithShadow(textRenderer, speed, 0, height - 40, 0x90FFFFFF);
        String position = String.format("Pos: (%.2f, %.2f, %.2f)", player.getX(), player.getY(), player.getZ());
        drawContext.drawTextWithShadow(textRenderer, position, 0, height - 30, 0x90FFFFFF);
        String blockPosStr = String.format("BPos: (%d, %d, %d)", blockPos.getX(), blockPos.getY(), blockPos.getZ());
        drawContext.drawTextWithShadow(textRenderer, blockPosStr, 0, height - 20, 0x90FFFFFF);
        String dir = String.format("Dir: (%.2f, %.2f)", MathHelper.wrapDegrees(player.getYaw()), player.getPitch());
        drawContext.drawTextWithShadow(textRenderer, dir, 0, height - 10, 0x90FFFFFF);

        matrixStack.pop();
    }

    public void drawFishingInfo(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        TextRenderer textRenderer = client.textRenderer;
        int height = client.getWindow().getScaledHeight();
        int width = client.getWindow().getScaledWidth();

        MatrixStack matrixStack = drawContext.getMatrices();
        matrixStack.push();

        if (player.fishHook == null) {
            String fishHookMsg = "Not fishing.";
            drawContext.drawTextWithShadow(textRenderer, fishHookMsg, 0, height - 30, TEXT_COLOR);
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
            drawContext.drawTextWithShadow(textRenderer, fishHookMsg, 0, height - 30, TEXT_COLOR);
        }

        String time = String.format("Time: %d, %d",
                client.world.getTime(), client.world.getTimeOfDay());
        drawContext.drawTextWithShadow(textRenderer, time, 0, height - 20, TEXT_COLOR);

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
