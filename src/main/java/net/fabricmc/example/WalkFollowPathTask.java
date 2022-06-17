package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.Optional;

public class WalkFollowPathTask implements Tickable {
    public static final Logger LOGGER = LogManager.getLogger("WalkFollowPathTask");

    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    private final WalkPath walkPath;

    private int curIdx;

    private boolean isCanceled;

    private boolean isFinished;

    private boolean isError;


    public WalkFollowPathTask(MinecraftClient client, WalkPath walkPath) {
        this.client = client;
        this.player = client.player;
        this.walkPath = walkPath;
        this.curIdx = 0;
        this.isCanceled = false;
        this.isFinished = false;
        this.isError = false;
    }

    @Override
    public boolean tick() {
        if (walkPath.size() < 2 || isCanceled) {
            LOGGER.info("Task canceled or no path there.");
            releaseAllKeysAndNotify();
            return false;
        }

        boolean isIndexUpdate = false;
        if (walkPath.size() > curIdx + 1 &&
                Objects.equals(walkPath.get(curIdx + 1).withY(0), player.getBlockPos().withY(0))) {
            curIdx = curIdx + 1;
            // LOGGER.info(String.format("Update current index %d", curIdx));
            isIndexUpdate = true;
        }

        if (curIdx == walkPath.size() - 1) {
            // finish
            LOGGER.info("Task finish");
            releaseAllKeysAndNotify();
            return false;
        }

        if (walkPath.get(curIdx).withY(0).getManhattanDistance(player.getBlockPos().withY(0)) > 10) {
            LOGGER.info("Not on the current path, exit.");
            isError = true;
            releaseAllKeysAndNotify();
            return false;
        }

        BlockPos currentBlock = walkPath.get(curIdx);
        BlockPos targetBlock = walkPath.get(curIdx + 1);
        int heightDiff = targetBlock.getY() - currentBlock.getY();

        float targetPitch = 20f;

        // if (heightDiff > 0) {
        //     targetPitch = 10f;
        // }
        // if (heightDiff < 0) {
        //     targetPitch = 60f;
        // }

        Optional<Float> targetYaw = MoveHelper.getTargetYaw(
                Vec3d.ofCenter(currentBlock), Vec3d.ofCenter(targetBlock)
        );

        player.setPitch(MoveHelper.changeAngle(player.getPitch(), targetPitch, 1.0f));
        if (targetYaw.isPresent()) {
            player.setYaw(MoveHelper.changeAngle(player.getYaw(), targetYaw.get(), 10.0f));
        }
        if (MathHelper.abs(MathHelper.wrapDegrees(player.getYaw() - targetYaw.get())) < 10) {
            client.options.forwardKey.setPressed(true);
            client.options.sneakKey.setPressed(false);
        } else {
            client.options.forwardKey.setPressed(false);
            client.options.sneakKey.setPressed(true);
        }

        boolean needJump = targetBlock.subtract(currentBlock).getY() > 0;

        client.options.jumpKey.setPressed(needJump && targetBlock.getY() - player.getY() > 0.5);
        Vec3d posOrig = player.getPos();
        Vec3d pos1 = posOrig.rotateY(targetYaw.get() * MathHelper.RADIANS_PER_DEGREE);
        Vec3d pos2 = Vec3d.ofBottomCenter(targetBlock).rotateY(targetYaw.get() * MathHelper.RADIANS_PER_DEGREE);

        if (isIndexUpdate) {
            LOGGER.info(String.format("Orig:%s %s and %s", posOrig, pos1, pos2));
        }

        if (pos2.getX() - pos1.getX() > 0.19) {
            client.options.leftKey.setPressed(true);
            client.options.rightKey.setPressed(false);
        } else if (pos2.getX() - pos1.getX() < -0.19) {
            client.options.rightKey.setPressed(true);
            client.options.leftKey.setPressed(false);
        } else {
            client.options.rightKey.setPressed(false);
            client.options.leftKey.setPressed(false);
        }

        return true;
    }

    private void releaseAllKeysAndNotify() {
        client.options.forwardKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        isFinished = true;
    }

    @Override
    public void cancel() {
        Tickable.super.cancel();
        this.isCanceled = true;
    }

    @Override
    public boolean isCanceled() {
        return isCanceled;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public boolean isError() {
        return isError;
    }
}