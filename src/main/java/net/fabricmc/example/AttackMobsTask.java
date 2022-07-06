package net.fabricmc.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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

            client.execute(() -> client.setScreen(null));
            Thread.sleep(500);

            client.options.inventoryKey.setPressed(true);
            KeyPressable.of(client.options.inventoryKey).onKeyPressed();
            Thread.sleep(200);
            client.options.inventoryKey.setPressed(false);

            if (client.currentScreen instanceof InventoryScreen) {
                InventoryScreen screen = (InventoryScreen) client.currentScreen;
                ScreenHandler handler = screen.getScreenHandler();

                PlayerSlots playerSlots = new PlayerSlots((PlayerScreenHandler) handler);
                LOGGER.info("Bag slots");
                printSlots(playerSlots.bagSlots);
                LOGGER.info("Shortcut slots");
                printSlots(playerSlots.shortcutSlots);
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

    private static void printSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            LOGGER.info("Slot %d: (%s, %d, %s)".formatted(
                    slot.id, slot.inventory.getClass().getSimpleName(), slot.getIndex(),
                    slot.inventory.getStack(slot.getIndex())));
        }
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("start").executes(CommandHelper.wrap(this::startAttackMobs)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopAttackMobs)));
    }


}
