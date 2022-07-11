package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.tick.Tick;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;

interface Tickable {
    boolean tick();

    default void cancel() {
    }

    boolean isCanceled();

    boolean isFinished();

    boolean isError();
}

interface Actionable {
    void onStart();

    void onTick();

    void onEnd();
}

public class PlayerMotion {
    public static final Logger LOGGER = LogManager.getLogger("PlayerMotion");

    private final MinecraftClient client;
    private ScheduledExecutorService executor;
    private final Queue<Tickable> tasks;
    private Tickable currentTask;

    private ScheduledFuture<?> scheduledFuture;

    public PlayerMotion(MinecraftClient client, ScheduledExecutorService executor) {
        this.client = client;
        this.executor = executor;
        this.tasks = new ConcurrentLinkedQueue<>();
        this.currentTask = null;
        this.scheduledFuture = executor.scheduleAtFixedRate(this::tick, 0, 10, TimeUnit.MILLISECONDS);
    }

    public void tick() {
        try {
            if (currentTask == null) {
                currentTask = tasks.poll();
            }

            if (currentTask != null) {
                boolean ret = currentTask.tick();
                if (!ret) {
                    currentTask = null;
                }
            }
        } catch (Exception ex) {
            LOGGER.catching(ex);
        }
    }

    public void addTask(Tickable tickable) {
        tasks.add(tickable);
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

    public void cancelAllTasks() {
        tasks.clear();
        if (currentTask != null) {
            currentTask.cancel();
        }
    }
}

class WalkToTask implements Tickable {

    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    private final Vec3d pos;
    private State state;
    private double lastDistance = Double.MAX_VALUE;

    private boolean isCanceled;
    private boolean isFinished;


    public WalkToTask(MinecraftClient client, Vec3d pos) {
        this.client = client;
        this.player = client.player;
        this.pos = pos;
        this.state = State.LOOK;
        this.isCanceled = false;
        this.isFinished = false;
    }

    @Override
    public boolean tick() {

        boolean ret = true;

        if (isCanceled) {
            client.options.forwardKey.setPressed(false);
            state = State.STOPPING;
        }

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
                isFinished = true;
                ret = false;
            }
        }

        return ret;
    }

    @Override
    public void cancel() {
        Tickable.super.cancel();
        isCanceled = true;
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
        return false;
    }

    private enum State {LOOK, MOVE, STOPPING}
}

class CallbackTask implements Tickable {
    private final Runnable runnable;
    private boolean isFinished = false;

    public CallbackTask(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public boolean tick() {
        runnable.run();
        isFinished = true;
        return false;
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public boolean isError() {
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
    private boolean isCanceled;
    private boolean isFinished;

    public LookDirection(MinecraftClient client, Vec3d pos) {
        this.client = client;
        this.pos = pos;
        this.player = client.player;
        targetYaw = 0;
        targetPitch = 0;
        status = Status.START;
        this.isCanceled = false;
    }

    @Override
    public boolean tick() {
        boolean ret = true;
        if (isCanceled) {
            status = Status.END;
        }
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

                if (Math.abs(MathHelper.angleBetween(currentPitch, targetPitch)) <  0.01f
                        && Math.abs(MathHelper.angleBetween(currentYaw, targetYaw)) < 0.01f) {
                    status = Status.END;
                    break;
                }

                player.setPitch(MoveHelper.changeAngle(currentPitch, targetPitch, maxPitchDelta));
                player.setYaw(MoveHelper.changeAngle(currentYaw, targetYaw, maxYawDelta));
            }
            case END -> {
                isFinished = true;
                ret = false;
            }
        }
        return ret;
    }

    @Override
    public void cancel() {
        Tickable.super.cancel();
        isCanceled = true;
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
        return false;
    }
}