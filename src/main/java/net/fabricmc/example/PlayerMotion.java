package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerMotion {

    private MinecraftClient client;
    private List<Tickable> tasks;

    public PlayerMotion(MinecraftClient client) {
        this.client = client;
        this.tasks = new ArrayList<>();
    }

    public void moveForward(int duration) {
        tasks.add(new ActionTask(duration, new Actionable() {
            @Override
            public void onStart() {
                client.send(()-> client.options.forwardKey.setPressed(true));
            }

            @Override
            public void onTick() {}

            @Override
            public void onEnd() {
                client.send(()-> client.options.forwardKey.setPressed(false));
            }
        }));
    }

    public void changeLookDirection(double deltaX, double deltaY, int duration) {
        tasks.add(new ActionTask(duration, new Actionable() {
            @Override
            public void onStart() {}

            @Override
            public void onTick() {
                client.player.changeLookDirection(deltaX, deltaY);
            }

            @Override
            public void onEnd() {}
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
}

interface Tickable {
    boolean tick();
}

interface Actionable {
    void onStart();
    void onTick();
    void onEnd();
}

class ActionTask implements Tickable {
    private int current;
    private int duration;
    private Actionable action;
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

    private MinecraftClient client;
    private ClientPlayerEntity player;
    private Vec3d pos;
    private enum State {LOOK, MOVE, STOPPING};
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

        var yaw = this.getTargetYaw();
        var pitch = this.getTargetPitch();
        if (yaw.isPresent()) {
            player.setYaw(changeAngle(player.getYaw(), yaw.get(), 1.5f));
        }

        if (pitch.isPresent()) {
            player.setPitch(changeAngle(player.getPitch(), pitch.get(), 1.0f));
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

    protected Optional<Float> getTargetPitch() {
        double d = pos.x - this.player.getX();
        double e = pos.y - this.player.getEyeY();
        double f = pos.z - this.player.getZ();
        double g = Math.sqrt(d * d + f * f);
        return Math.abs(e) > (double)1.0E-5f || Math.abs(g) > (double)1.0E-5f ?
                Optional.of((float) -Math.toDegrees(MathHelper.atan2(e, g))) :
                Optional.empty();
    }

    protected Optional<Float> getTargetYaw() {
        double d = pos.x - this.player.getX();
        double e = pos.z - this.player.getZ();
        return Math.abs(e) > (double)1.0E-5f || Math.abs(d) > (double)1.0E-5f ?
                Optional.of((float) (Math.toDegrees(MathHelper.atan2(e, d))) - 90.0f) :
                Optional.empty();
    }

    protected float changeAngle(float from, float to, float max) {
        float f = MathHelper.subtractAngles(from, to);
        float g = MathHelper.clamp(f, -max, max);
        return from + g;
    }

}

class CallbackTask implements Tickable {
    private Runnable runnable;

    public CallbackTask(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public boolean tick() {
        runnable.run();
        return false;
    }
}
