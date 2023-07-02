package net.fabricmc.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ScheduledExecutorService;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PlayerMotionManager {
    public static final Logger LOGGER = LogManager.getLogger("PlayerMotionManager");

    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;

    private PlayerMotion playerMotion;

    private TransferItemTask transferItemTask;

    private AttackMobsTask attackMobsTask;

    private NaiveFishingTask naiveFishingTask;


    public PlayerMotionManager(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        playerMotion = new PlayerMotion(client, executor);

        transferItemTask = new TransferItemTask(client, executor, xrayRender, playerMotion);
        attackMobsTask = new AttackMobsTask(client, executor, xrayRender, playerMotion);
        naiveFishingTask = new NaiveFishingTask(client, executor, xrayRender, playerMotion);
    }


    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(transferItemTask.registerCommand(literal("transferItem")))
                .then(attackMobsTask.registerCommand(literal("attackMobs")))
                .then(naiveFishingTask.registerCommand(literal("naiveFishing")));
    }

}
