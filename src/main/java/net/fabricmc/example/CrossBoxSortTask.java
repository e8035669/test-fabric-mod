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
import net.minecraft.client.option.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

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

    private static void waitFor(BooleanSupplier func, int times, int checkInterval, boolean throwOnTimeout) {
        boolean success = false;
        while (times > 0) {
            times--;
            if (func.getAsBoolean()) {
                success = true;
                break;
            }
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException ex) {
                LOGGER.warn(ex);
            }
        }
        if (!success && throwOnTimeout) {
            throw new RuntimeException("Wait Timeout");
        }
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

    private void updateRenders() {
        renders.clear();

        for (BlockPos blockPos : this.currentSelectedBoxes) {
            renders.add(new CuboidRender(blockPos, 0xFF00FF00));
        }
        this.walkPathRender.ifPresent(renders::add);
    }

    private boolean tryAddSelectedBox(BlockPos blockPos) {
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
        return canAdd;
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
                isBoxOpened = true;
            }


        } catch (Exception ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text = Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            walkPathRender = Optional.empty();
            updateRenders();
            playerMotion.cancelAllTasks();
            client.options.useKey.setPressed(false);
            HotPlugMouse.plugMouse(client);
            LOGGER.info("Cross box sorting finish");
            client.player.sendMessage(Text.literal("Cross box sorting task finish"));
        }
    }

    public int startSortBoxes(CommandContext<FabricClientCommandSource> context) {
        EnchantmentTargetAdapter target = context.getArgument("target", EnchantmentTargetAdapter.class);
        if (future == null || future.isDone()) {
            future = executor.schedule(() -> this.doSortBooks(target.target), 1000, TimeUnit.MILLISECONDS);
        }
        return Command.SINGLE_SUCCESS;
    }

    public void startSortBoxesAccordHand() {
        ItemStack itemStack = client.player.getInventory().getMainHandStack();
        client.player.sendMessage(itemStack.getItem().getName());
        MutableText texts = Text.literal("Accept target:");
        for (EnchantmentTarget t : EnchantmentTarget.values()) {
            if (t.isAcceptableItem(itemStack.getItem())) {
                texts.append(t.toString()).append(", ");
            }
        }
        client.player.sendMessage(texts);
    }

    public void doSortBooks(EnchantmentTarget target) {
        try {
            client.player.sendMessage(Text.literal("Start sort books"));
            this.checkStatus();

            int[] movingBuffer = this.getMovingBuffer();
            String msg = String.format("Has %d empty slot to move", movingBuffer.length);
            client.player.sendMessage(Text.literal(msg));

            msg = "Start check boxes";
            client.player.sendMessage(Text.literal(msg));

            Map<BlockPos, List<Slot>> books = new HashMap<>();

            for (BlockPos blockPos : this.currentSelectedBoxes) {
                this.moveToNearBlock(blockPos);
                this.pressKey(client.options.useKey);
                waitFor(() -> client.currentScreen instanceof GenericContainerScreen,
                        10, 50, true);

                GenericContainerSlots slots = new GenericContainerSlots(client.currentScreen);
                List<Slot> booksInBox = slots.boxSlots.stream()
                        .filter(s -> s.getStack().isOf(Items.ENCHANTED_BOOK))
                        .toList();
                books.put(blockPos, booksInBox);

                client.send(this::closeScreen);
                Thread.sleep(500);
            }
            client.player.sendMessage(Text.literal("List books"));

            for (BlockPos blockPos : this.currentSelectedBoxes) {
                List<Slot> slots = books.get(blockPos);
                client.player.sendMessage(Text.literal("Box %s".formatted(blockPos)));
                for (Slot s : slots) {
                    Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(s.getStack());
                    MutableText text = Text.literal("Slot %d:".formatted(s.getIndex()));
                    for (Map.Entry<Enchantment, Integer> e : enchantments.entrySet()) {
                        text.append(" ").append(e.getKey().getName(e.getValue()));
                    }
                    client.player.sendMessage(text);
                }
            }

            client.player.sendMessage(Text.literal("Finish sort books"));
        } catch (Exception ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text = Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            walkPathRender = Optional.empty();
            updateRenders();
            playerMotion.cancelAllTasks();
            client.options.useKey.setPressed(false);
            HotPlugMouse.plugMouse(client);
            LOGGER.info("Cross box sorting finish");
            client.player.sendMessage(Text.literal("Cross box sorting task finish"));
        }
    }

    private void checkStatus() {
        if (this.currentSelectedBoxes.isEmpty()) {
            throw new RuntimeException("No selected boxes");
        }
        if (client.player.getInventory().getEmptySlot() == -1) {
            throw new RuntimeException("Need at least one empty slot.");
        }
    }

    private int[] getMovingBuffer() {
        List<ItemStack> mainBag = client.player.getInventory().main;
        int[] movingBuffer = IntStream.range(0, mainBag.size())
                .filter(i -> mainBag.get(i).isEmpty())
                .toArray();
        return movingBuffer;
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

    private void moveToNearBlock(BlockPos sourceBlockPos) throws InterruptedException {
        AStarSearch aStarSearch = new AStarSearch(client, client.player.getBlockPos(), sourceBlockPos);
        aStarSearch.setNearMode(true);
        Optional<WalkPath> walkPath = aStarSearch.search();
        if (walkPath.isPresent()) {
            WalkFollowPathTask walkFollowPathTask = new WalkFollowPathTask(client, walkPath.get());
            playerMotion.addTask(walkFollowPathTask);
            walkPathRender = Optional.of(new WalkPathRender(walkPath.get(), 0xA0FFFFFF));
            updateRenders();
            LOGGER.info("Wait for WalkFollowPathTask to finish.");
            while (!walkFollowPathTask.isFinished()) {
                Thread.sleep(10);
            }
            LOGGER.info("Wait for WalkFollowPathTask is finished.");
        }

        Thread.sleep(200);

        playerMotion.lookDirection(Vec3d.ofCenter(sourceBlockPos));

        while (!playerMotion.isTaskEmpty()) {
            Thread.sleep(10);
        }
        Thread.sleep(200);
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

    private void closeScreen() {
        client.setScreen(null);
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("startRememberBoxes").executes(CommandHelper.wrap(this::startRememberBoxes)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopProcess)))
                .then(literal("startSortBooks")
                        .then(argument("target", new EnchantmentArgumentType())
                                .executes(this::startSortBoxes)))
                .then(literal("startSortBoxesAccordHand").executes(CommandHelper.wrap(this::startSortBoxesAccordHand)));
    }
}
