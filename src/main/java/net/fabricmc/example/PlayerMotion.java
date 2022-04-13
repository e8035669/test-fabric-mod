package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class PlayerMotion {
    interface Actionable {
        void onStart();
        void onTick();
        void onEnd();
    }

    private class ActionTask {
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

    private MinecraftClient client;
    private List<ActionTask> tasks;

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
            public void onTick() {

            }

            @Override
            public void onEnd() {
                client.send(()-> client.options.forwardKey.setPressed(false));
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
}
