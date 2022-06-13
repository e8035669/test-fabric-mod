package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

interface Tickable {
    boolean tick();
}

interface Actionable {
    void onStart();

    void onTick();

    void onEnd();
}

public class PlayerMotion {
    public static final Logger LOGGER = LogManager.getLogger("PlayerMotion");

    private final MinecraftClient client;
    private final List<Tickable> tasks;

    public PlayerMotion(MinecraftClient client) {
        this.client = client;
        this.tasks = new ArrayList<>();
    }

    public void moveForward(int duration) {
        tasks.add(new ActionTask(duration, new Actionable() {
            @Override
            public void onStart() {
                client.send(() -> client.options.forwardKey.setPressed(true));
            }

            @Override
            public void onTick() {
            }

            @Override
            public void onEnd() {
                client.send(() -> client.options.forwardKey.setPressed(false));
            }
        }));
    }

    public void changeLookDirection(double deltaX, double deltaY, int duration) {
        tasks.add(new ActionTask(duration, new Actionable() {
            @Override
            public void onStart() {
            }

            @Override
            public void onTick() {
                client.player.changeLookDirection(deltaX, deltaY);
            }

            @Override
            public void onEnd() {
            }
        }));
    }

    public void tick() {
        if (!tasks.isEmpty()) {
            boolean ret = tasks.get(0).tick();
            if (!ret) {
                tasks.remove(0);
            }
        }
    }

    public void walkTo(Vec3d pos) {
        tasks.add(new WalkToTask(client, pos));
    }

    public void send(Runnable runnable) {
        tasks.add(new CallbackTask(runnable));
    }

    public void walkFollowPath(WalkPath walkPath) {
        tasks.add(new WalkFollowPathTask(client, walkPath));
    }

    public boolean isTaskEmpty() {
        return tasks.isEmpty();
    }
}

class ActionTask implements Tickable {
    private final int duration;
    private final Actionable action;
    private int current;

    public ActionTask(int duration, Actionable action) {
        this.action = action;
        this.duration = duration;
        this.current = 0;
    }

    public boolean tick() {
        if (current == 0) {
            action.onStart();
        }
        action.onTick();
        current++;
        if (current >= duration) {
            action.onEnd();
            return false;
        } else {
            return true;
        }
    }
}

class WalkToTask implements Tickable {

    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    private final Vec3d pos;
    private State state;
    private double lastDistance = Double.MAX_VALUE;
    public WalkToTask(MinecraftClient client, Vec3d pos) {
        this.client = client;
        this.player = client.player;
        this.pos = pos;
        this.state = State.LOOK;
    }

    @Override
    public boolean tick() {

        boolean ret = true;

        var yaw = MoveHelper.getTargetYaw(player, pos);
        var pitch = MoveHelper.getTargetPitch(player, pos);
        if (yaw.isPresent()) {
            player.setYaw(MoveHelper.changeAngle(player.getYaw(), yaw.get(), 1.5f));
        }

        if (pitch.isPresent()) {
            player.setPitch(MoveHelper.changeAngle(player.getPitch(), pitch.get(), 1.0f));
        }

        if (MathHelper.abs(MathHelper.subtractAngles(player.getYaw(), yaw.get())) < 45) {
            if (state == State.LOOK) {
                state = State.MOVE;
                client.options.forwardKey.setPressed(true);
            }
        }

        if (state == State.MOVE) {
            double currentDistance = player.getPos().squaredDistanceTo(pos);
            if (currentDistance < 3 * 3 || currentDistance > this.lastDistance) {
                client.options.forwardKey.setPressed(false);
                state = State.STOPPING;
            }
            this.lastDistance = currentDistance;
        }

        if (state == State.STOPPING) {
            if (MathHelper.approximatelyEquals(player.getVelocity().horizontalLengthSquared(), 0)) {
                ret = false;
            }
        }

        return ret;
    }

    private enum State {LOOK, MOVE, STOPPING}
}

class CallbackTask implements Tickable {
    private final Runnable runnable;

    public CallbackTask(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public boolean tick() {
        runnable.run();
        return false;
    }
}

class WalkFollowPathTask implements Tickable {
    public static final Logger LOGGER = LogManager.getLogger("WalkFollowPathTask");

    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    private final WalkPath walkPath;

    private int curIdx;


    public WalkFollowPathTask(MinecraftClient client, WalkPath walkPath) {
        this.client = client;
        this.player = client.player;
        this.walkPath = walkPath;
        this.curIdx = 0;
    }

    @Override
    public boolean tick() {
        if (walkPath.size() < 2) {
            return false;
        }

        boolean isIndexUpdate = false;
        if (Objects.equals(walkPath.get(curIdx + 1).withY(0), player.getBlockPos().withY(0))) {
            curIdx = curIdx + 1;
            LOGGER.info(String.format("Update current index %d", curIdx));
            isIndexUpdate = true;
        }

        if (curIdx == walkPath.size() - 1) {
            // finish
            LOGGER.info("Task finish");
            client.options.forwardKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            return false;
        }

        if (walkPath.get(curIdx).withY(0).getManhattanDistance(player.getBlockPos().withY(0)) > 10) {
            LOGGER.info("Not on the current path, exit.");
            client.options.forwardKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
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
}

