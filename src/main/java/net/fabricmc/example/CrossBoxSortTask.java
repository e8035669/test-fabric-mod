package net.fabricmc.example;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CrossBoxSortTask {
    public static final Logger LOGGER = LogManager.getLogger("CrossBoxSortTask");
    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;
    private XrayRenders renders;
    private PlayerMotion playerMotion;
    private ScheduledFuture<?> future;
    private Optional<WalkPathRender> walkPathRender;

    private List<BlockPos> currentSelectedBoxes;

    public CrossBoxSortTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender, PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;

        this.renders = new XrayRenders();
        this.xrayRender.add(this.renders);
        this.currentSelectedBoxes = new ArrayList<>();
        this.walkPathRender = Optional.empty();
    }

    public void startRememberBoxes() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::doRememberBoxes, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopProcess() {
        if (future != null) {
            future.cancel(true);
        }
    }

    public void startSorting() {

    }

    private void updateRenders() {
        renders.clear();

        for (BlockPos blockPos : this.currentSelectedBoxes) {
            renders.add(new CuboidRender(blockPos, 0xFF000000));
        }
        this.walkPathRender.ifPresent(renders::add);
    }

    private void tryAddSelectedBox(BlockPos blockPos) {
        boolean canAdd = true;
        for (BlockPos b : this.currentSelectedBoxes) {
            if (b.equals(blockPos)) {
                canAdd = false;
                break;
            }
            Optional<BlockPos> neighbor = this.getNeighborChestPos(b);
            if (neighbor.isPresent()) {
                if (neighbor.get().equals(blockPos)) {
                    canAdd = false;
                    break;
                }
            }
        }

        if (canAdd) {
            this.currentSelectedBoxes.add(blockPos);
            this.updateRenders();
        }
    }

    private void cleanSelectedBoxes() {
        this.currentSelectedBoxes.clear();
        this.updateRenders();
    }

    public void doRememberBoxes() {

        try {
            this.cleanSelectedBoxes();
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
                    Thread.sleep(500);
                    continue;
                }

                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos blockPos = blockHitResult.getBlockPos();
                if (!client.world.getBlockState(blockPos).isOf(Blocks.CHEST)) {
                    Thread.sleep(500);
                    continue;
                }
//                Optional<BlockPos> neighborChestPos = getNeighborChestPos(blockPos);
//                LOGGER.info(String.format(""));
//                LOGGER.info(String.format("Box pos %s", blockPos));
//                LOGGER.info(String.format("Neighbor Box pos %s", neighborChestPos));

                boolean hasEnchantedBook = false;

                List<Slot> items = InventoryManager.selectAllItemInBox(client);
                for (Slot slot : items) {
                    ItemStack itemStack = slot.getStack();
                    if (itemStack.isOf(Items.ENCHANTED_BOOK)) {
                        hasEnchantedBook = true;
                        break;
                    }
                }

                if (hasEnchantedBook) {
                    this.tryAddSelectedBox(blockPos);
                }
                /*
                for (int i = 0; i < items.size(); ++i) {
                    Slot slot = items.get(i);
                    ItemStack itemStack = slot.getStack();
                    if (itemStack.isOf(Items.ENCHANTED_BOOK)) {

                        NbtList enchantment = EnchantedBookItem.getEnchantmentNbt(itemStack);
                        Map<Enchantment, Integer> enchantments = EnchantmentHelper.fromNbt(enchantment);
                        LOGGER.info(String.format("%s", enchantments));
                        for (Map.Entry<Enchantment, Integer> entry: enchantments.entrySet()) {
                            LOGGER.info("Key: {} {}, Value: {}", entry.getKey(), entry.getKey().getClass(), entry.getValue());
                        }
                        LOGGER.info(String.format("Item %d: %s %s", i, itemStack, enchantment));
                    }
                }
                */

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


    public int doSortBooks(CommandContext<FabricClientCommandSource> context) {
        EnchantmentTargetAdapter target = context.getArgument("target", EnchantmentTargetAdapter.class);
        this.doSortBooks0(target.target);
        return Command.SINGLE_SUCCESS;
    }

    public void doSortBooks0(EnchantmentTarget target) {
        try {
            client.player.sendMessage(Text.literal("Start sort books"));



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

    private Optional<BlockPos> getNeighborChestPos(BlockPos blockPos) {
        Optional<BlockPos> ret = Optional.empty();
        BlockState blockState = client.world.getBlockState(blockPos);
        if (blockState.isOf(Blocks.CHEST)) {
            ChestType chestType = blockState.get(ChestBlock.CHEST_TYPE);
            if (chestType != ChestType.SINGLE) {
                Direction direction = ChestBlock.getFacing(blockState);
                ret = Optional.of(blockPos.add(direction.getVector()));
            }
        }
        return ret;
    }


    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("startRememberBoxes").executes(CommandHelper.wrap(this::startRememberBoxes)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopProcess)))
                .then(literal("startSortBooks")
                        .then(argument("target", new EnchantmentArgumentType())
                                .executes(this::doSortBooks)));
    }
}
