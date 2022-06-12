package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerMotionManager {

    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;

    private PlayerMotion playerMotion;


    public PlayerMotionManager(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;

        playerMotion = new PlayerMotion(client);
        executor.scheduleAtFixedRate(() -> playerMotion.tick(), 0, 10, TimeUnit.MILLISECONDS);
    }

    public void tagSourceBox() {

    }

    public void tagTargetBox() {

    }

    public void startTransferItems() {

    }

}
