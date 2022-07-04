package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.tools.picocli.CommandLine;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AttackMobsTask {
    public static final Logger LOGGER = LogManager.getLogger("AttackMobsTask");

    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;
    private PlayerMotion playerMotion;

    private ScheduledFuture<?> future;

    public AttackMobsTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender, PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;
        this.future = null;
    }

    public void startAttackMobs() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::doAttackMobs, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopAttackMobs() {
        future.cancel(true);
    }

    public void doAttackMobs() {
        try {
            HotPlugMouse.unplugMouse(client);

            while (!future.isCancelled()) {
                break;


            }

        } catch (Exception ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text =
                    Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            client.options.attackKey.setPressed(false);
            HotPlugMouse.plugMouse(client);
            LOGGER.info("Attack mob task finish");
            client.player.sendMessage(Text.literal("Attack mob task finish"));
        }
    }


}
