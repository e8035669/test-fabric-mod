package net.fabricmc.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CrossBoxSortTask {
    public static final Logger LOGGER = LogManager.getLogger("CrossBoxSortTask");
    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;
    private PlayerMotion playerMotion;
    private ScheduledFuture<?> future;
    private Optional<WalkPathRender> walkPathRender;

    public CrossBoxSortTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender, PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;
    }

    public void startProcess() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::doProcess, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopProcess() {
        if (future != null) {
            future.cancel(true);
        }
    }

    public void startSorting() {

    }

    public void doProcess() {
        try {
            client.player.sendMessage(Text.literal("Start watching boxes"));

            boolean isBoxOpened = false;

            while (!future.isCancelled()) {
                // Detect open boxes
                if (!(client.currentScreen instanceof GenericContainerScreen)) {
                    isBoxOpened = false;
                    Thread.sleep(500);
                    continue;
                }

                if (isBoxOpened) {
                    // Already opened
                    Thread.sleep(500);
                    continue;
                }

                HitResult hitResult = client.crosshairTarget;
                if (hitResult.getType() != HitResult.Type.BLOCK) {
                    continue;
                }

                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos blockPos = blockHitResult.getBlockPos();
                LOGGER.info(String.format("Box pos %s", blockPos));

                List<Slot> items = InventoryManager.selectAllItemInBox(client);
                for (int i = 0; i < items.size(); ++i) {
                    Slot slot = items.get(i);
                    ItemStack itemStack = slot.getStack();
                    Item item = itemStack.getItem();
                    if (item == Items.ENCHANTED_BOOK) {
                        NbtList enchantment = EnchantedBookItem.getEnchantmentNbt(itemStack);
                        for (int j = 0; j < enchantment.size(); ++j) {
                            NbtElement element = enchantment.get(j);
                            LOGGER.info(String.format("nbt %s", element));
                        }
                        LOGGER.info(String.format("Item %d: %s %s", i, itemStack, enchantment));
                    }
                }

                isBoxOpened = true;
            }

        } catch (Exception ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text = Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
            playerMotion.cancelAllTasks();
            client.options.useKey.setPressed(false);
            HotPlugMouse.plugMouse(client);
            LOGGER.info("Cross box sorting finish");
            client.player.sendMessage(Text.literal("Cross box sorting task finish"));
        }
    }

    private void throwOnCancelled(ScheduledFuture<?> future) {
        if (future.isCancelled()) {
            throw new RuntimeException("Future Cancelled");
        }
    }


    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("start").executes(CommandHelper.wrap(this::startProcess)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopProcess)));
    }
}
