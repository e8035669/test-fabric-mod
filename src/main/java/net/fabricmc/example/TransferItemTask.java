package net.fabricmc.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TransferItemTask {
    public static final Logger LOGGER = LogManager.getLogger("TransferItemTask");

    private MinecraftClient client;
    private ScheduledExecutorService executor;

    private XrayRender xrayRender;
    private PlayerMotion playerMotion;

    private BlockPos sourceBoxPos;
    private CuboidRender sourceBoxRender;
    private BlockPos targetBoxPos;
    private CuboidRender targetBoxRender;

    private ScheduledFuture<?> future;
    private Optional<WalkPathRender> walkPathRender;

    private boolean isNeedStop;


    public TransferItemTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender,
                            PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;
        walkPathRender = Optional.empty();
        this.isNeedStop = false;
    }

    public void tagSourceBox() {
        HitResult hr = client.crosshairTarget;
        boolean isSuccess = false;
        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockPos blockPos = bhr.getBlockPos();
            BlockState blockState = client.world.getBlockState(blockPos);
            if (blockState.isOf(Blocks.CHEST)) {
                sourceBoxPos = blockPos;

                xrayRender.remove(sourceBoxRender);
                sourceBoxRender = new CuboidRender(blockPos, 0xFF0000FF);
                xrayRender.add(sourceBoxRender);
                isSuccess = true;
            }
        }
        if (!isSuccess) {
            xrayRender.remove(sourceBoxRender);
            sourceBoxPos = null;
            client.player.sendMessage(Text.literal("Source box is unset"));
        }
    }

    public void tagTargetBox() {
        HitResult hr = client.crosshairTarget;
        boolean isSuccess = false;
        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockPos blockPos = bhr.getBlockPos();
            BlockState blockState = client.world.getBlockState(blockPos);
            if (blockState.isOf(Blocks.CHEST)) {
                targetBoxPos = blockPos;

                xrayRender.remove(targetBoxRender);
                targetBoxRender = new CuboidRender(blockPos, 0xFF00FF00);
                xrayRender.getRenderables().add(targetBoxRender);
                isSuccess = true;
            }
        }
        if (!isSuccess) {
            xrayRender.remove(targetBoxRender);
            targetBoxPos = null;
            client.player.sendMessage(Text.literal("Target box is unset"));
        }
    }


    public void startTransferItems() {
        Objects.requireNonNull(sourceBoxPos);
        Objects.requireNonNull(targetBoxPos);

        this.startTransferItem0();
    }

    public void stopTransferItems() {
        this.stopTransferItem0();
    }

    public void startTransferItem0() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::doTransferItem, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopTransferItem0() {
        future.cancel(true);
    }

    public void doTransferItem() {
        HotPlugMouse.unplugMouse(client);
        try {
            while (!future.isCancelled()) {
                moveToNearBlock(sourceBoxPos);
                client.execute(() -> client.currentScreen.close());
                Thread.sleep(500);

                client.options.useKey.setPressed(true);
                Thread.sleep(100);
                client.options.useKey.setPressed(false);
                Thread.sleep(500);

                if (!(client.currentScreen instanceof GenericContainerScreen)) {
                    throw new RuntimeException("Screen not open");
                }

                int bagOffset = InventoryManager.getBagOffset(client);
                List<Slot> itemsInBox = InventoryManager.selectAllItemInBox(client);
                List<Slot> bagSlots = InventoryManager.selectAllEmptyInBag(client);
                LOGGER.info(itemsInBox);

                if (!itemsInBox.isEmpty()) {
                    InventoryManager.transferSlots(client, itemsInBox, bagSlots);
                    Thread.sleep(500);
                } else {
                    Thread.sleep(100);
                    client.execute(() -> client.currentScreen.close());
                    client.player.sendMessage(Text.literal("Nothing in the box."));
                    break;
                }

                client.execute(() -> client.currentScreen.close());

                moveToNearBlock(targetBoxPos);
                client.execute(() -> client.currentScreen.close());
                Thread.sleep(500);

                client.options.useKey.setPressed(true);
                Thread.sleep(100);
                client.options.useKey.setPressed(false);
                Thread.sleep(500);

                if (!(client.currentScreen instanceof GenericContainerScreen)) {
                    throw new RuntimeException("Screen not open");
                }

                int bagOffset2 = InventoryManager.getBagOffset(client);
                List<Integer> bagSlotIds = bagSlots.stream().map(s -> s.id + (bagOffset2 - bagOffset)).toList();
                List<Integer> boxEmptySlot = InventoryManager.selectAllEmptyInBox(client).stream().map(s -> s.id).toList();

                InventoryManager.transferSlotIds(client, bagSlotIds, boxEmptySlot);
                Thread.sleep(500);

                client.execute(() -> client.currentScreen.close());
            }

        } catch (Exception ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text =
                    Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
            playerMotion.cancelAllTasks();
            client.options.useKey.setPressed(false);
            HotPlugMouse.plugMouse(client);
            LOGGER.info("Transfer task Finish");
            client.player.sendMessage(Text.literal("Transfer task finish"));
        }
    }

    private void moveToNearBlock(BlockPos sourceBlockPos) throws InterruptedException {
        List<BlockPos> blocksNear = AStarSearch.findNearFloor(client.world, sourceBlockPos, 1);
        Optional<BlockPos> nearestBlock = AStarSearch.findNearestOne(blocksNear, client.player.getPos());
        if (nearestBlock.isPresent()) {
            AStarSearch aStarSearch = new AStarSearch(client, client.player.getBlockPos(), nearestBlock.get());
            Optional<WalkPath> walkPath = aStarSearch.search();
            if (walkPath.isPresent()) {

                WalkFollowPathTask walkFollowPathTask = new WalkFollowPathTask(client, walkPath.get());
                playerMotion.addTask(walkFollowPathTask);

                walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
                walkPathRender = Optional.of(new WalkPathRender(walkPath.get(), 0xA0FFFFFF));
                xrayRender.getRenderables().add(walkPathRender.get());
                LOGGER.info("Wait for WalkFollowPathTask to finish.");
                while (!walkFollowPathTask.isFinished()) {
                    Thread.sleep(10);
                }
                LOGGER.info("Wait for WalkFollowPathTask is finished.");
            }
        }

        playerMotion.lookDirection(Vec3d.ofCenter(sourceBlockPos));

        while (!playerMotion.isTaskEmpty()) {
            Thread.sleep(10);
        }
    }


    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("source").executes(CommandHelper.wrap(this::tagSourceBox)))
                .then(literal("target").executes(CommandHelper.wrap(this::tagTargetBox)))
                .then(literal("start").executes(CommandHelper.wrap(this::startTransferItems)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopTransferItems)));
    }
}

