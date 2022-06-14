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

    public void lookDirection(Vec3d pos) {
        tasks.add(new LookDirection(client, pos));
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

class LookDirection implements Tickable {

    private static float MAX_YAW_DELTA = 10.0f;
    private static float MAX_PITCH_DELTA = 1.0f;
    private enum Status {START, MOVING, END};


    private MinecraftClient client;
    private Vec3d pos;
    private ClientPlayerEntity player;

    private float targetYaw;
    private float targetPitch;

    private float maxYawDelta;
    private float maxPitchDelta;
    private Status status;

    public LookDirection(MinecraftClient client, Vec3d pos) {
        this.client = client;
        this.pos = pos;
        this.player = client.player;
        targetYaw = 0;
        targetPitch = 0;
        status = Status.START;
    }

    @Override
    public boolean tick() {
        boolean ret = true;
        switch (status) {
            case START -> {
                var yaw = MoveHelper.getTargetYaw(player, pos);
                var pitch = MoveHelper.getTargetPitch(player, pos);

                if (yaw.isPresent()) {
                    targetYaw = yaw.get();
                }
                if (pitch.isPresent()) {
                    targetPitch = pitch.get();
                }

                float yawDiff = MathHelper.abs(MathHelper.subtractAngles(player.getYaw(), targetYaw));
                float pitchDiff = MathHelper.abs(MathHelper.subtractAngles(player.getPitch(), targetPitch));

                int steps = Math.max((int)Math.max(yawDiff / MAX_YAW_DELTA, pitchDiff / MAX_PITCH_DELTA), 1);
                this.maxYawDelta = yawDiff / steps;
                this.maxPitchDelta = pitchDiff / steps;
                status = Status.MOVING;
            }
            case MOVING -> {
                float currentPitch = player.getPitch();
                float currentYaw = player.getYaw();

                if (MathHelper.approximatelyEquals(MathHelper.angleBetween(currentPitch, targetPitch), 0.0f)
                        && MathHelper.approximatelyEquals(MathHelper.angleBetween(currentYaw, targetYaw), 0.0f)) {
                    status = Status.END;
                    break;
                }

                player.setPitch(MoveHelper.changeAngle(currentPitch, targetPitch, maxPitchDelta));
                player.setYaw(MoveHelper.changeAngle(currentYaw, targetYaw, maxYawDelta));
            }
            case END -> ret = false;
        }
        return ret;
    }
}