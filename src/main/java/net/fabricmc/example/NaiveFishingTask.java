package net.fabricmc.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NaiveFishingTask {
    public static final Logger LOGGER = LogManager.getLogger("TransferItemTask");
    private MinecraftClient client;
    private ScheduledExecutorService executor;

    private XrayRender xrayRender;
    private PlayerMotion playerMotion;

    private ScheduledFuture<?> future;

    public NaiveFishingTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender, PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;
        this.future = null;
    }


    public void startFishing() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::fishingTask, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopFishing() {
        future.cancel(true);
    }

    public void fishingTask() {
        HotPlugMouse.unplugMouse(client);
        try {
            while (true) {
                if (!client.player.getStackInHand(Hand.MAIN_HAND).isOf(Items.FISHING_ROD)) {
                    List<ItemStack> hotBar = PlayerSlots.getHotBarItems(client);
                    int[] rodIndex = filterItem(hotBar, Items.FISHING_ROD);
                    if (rodIndex.length == 0) {
                        LOGGER.info("No fishing rod");
                        break;
                    }

                    pressKey(client.options.hotbarKeys[rodIndex[0]]);
                }

                pressKey(client.options.useKey, 60);

                int timeout = 50;
                while (timeout-- > 0) {
                    if (client.player.fishHook != null) {
                        break;
                    }
                    Thread.sleep(20);
                }

                FishingBobberEntity fishHook = client.player.fishHook;
                Fishable fishable = Fishable.of(fishHook);

                timeout = 50;
                while (timeout-- > 0) {
                    if (fishable.getState() != FishingBobberEntity.State.FLYING) {
                        break;
                    }
                    Thread.sleep(20);
                }

                if (fishable.getState() == FishingBobberEntity.State.HOOKED_IN_ENTITY) {
                    int nextSlot = client.player.getInventory().selectedSlot;
                    nextSlot = (nextSlot + 1) % 9;
                    pressKey(client.options.hotbarKeys[nextSlot]);
                    continue;
                }
                timeout = 3000;
                while (timeout -- > 0) {
                    if (fishable.isCaughtFish()) {
                        break;
                    }
                    Thread.sleep(20);
                }
                pressKey(client.options.useKey, 60);
                Thread.sleep(500);
            }
        } catch (Exception ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text =
                    Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            HotPlugMouse.plugMouse(client);
        }
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("start").executes(CommandHelper.wrap(this::startFishing)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopFishing)));
    }

    private void pressKey(KeyBinding keyBinding, int sleepTime) throws InterruptedException {
        keyBinding.setPressed(true);
        KeyPressable.of(keyBinding).onKeyPressed();
        Thread.sleep(sleepTime);
        keyBinding.setPressed(false);
    }

    private void pressKey(KeyBinding keyBinding) throws InterruptedException {
        pressKey(keyBinding, 200);
    }

    private void pressInventoryKey() throws InterruptedException {
        pressKey(client.options.inventoryKey);
    }



    private int[] filterItem(List<ItemStack> itemStacks, Item item) {
        return IntStream.range(0, itemStacks.size())
                .filter((i) -> itemStacks.get(i).isOf(item))
                .toArray();
    }
}
