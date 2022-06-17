package net.fabricmc.example;

import net.fabricmc.example.mixin.MouseMixin;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlayerMotionManager {
    public static final Logger LOGGER = LogManager.getLogger("PlayerMotionManager");

    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;

    private PlayerMotion playerMotion;

    private Optional<BlockPos> sourceBoxPos;
    private Optional<CuboidRender> sourceBoxRender;
    private Optional<BlockPos> targetBoxPos;
    private Optional<CuboidRender> targetBoxRender;

    private TransferItemTask transferItemTask;


    public PlayerMotionManager(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        playerMotion = new PlayerMotion(client, executor);
        sourceBoxPos = Optional.empty();
        sourceBoxRender = Optional.empty();
        targetBoxPos = Optional.empty();
        targetBoxRender = Optional.empty();

        transferItemTask = new TransferItemTask(client, executor, xrayRender, playerMotion);
    }

    public void tagSourceBox() {
        HitResult hr = client.crosshairTarget;
        boolean isSuccess = false;
        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockPos blockPos = bhr.getBlockPos();
            BlockState blockState = client.world.getBlockState(blockPos);
            if (blockState.isOf(Blocks.CHEST)) {
                sourceBoxPos = Optional.of(blockPos);

                sourceBoxRender.ifPresent(xrayRender.getRenderables()::remove);
                sourceBoxRender = Optional.of(new CuboidRender(blockPos, 0xFF0000FF));
                xrayRender.getRenderables().add(sourceBoxRender.get());
                isSuccess = true;
            }
        }
        if (!isSuccess) {
            sourceBoxRender.ifPresent(xrayRender.getRenderables()::remove);
            sourceBoxPos = Optional.empty();
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
                targetBoxPos = Optional.of(blockPos);

                targetBoxRender.ifPresent(xrayRender.getRenderables()::remove);
                targetBoxRender = Optional.of(new CuboidRender(blockPos, 0xFF00FF00));
                xrayRender.getRenderables().add(targetBoxRender.get());
                isSuccess = true;
            }
        }
        if (!isSuccess) {
            targetBoxRender.ifPresent(xrayRender.getRenderables()::remove);
            targetBoxPos = Optional.empty();
            client.player.sendMessage(Text.literal("Target box is unset"));
        }
    }



    public void startTransferItems() {
        if (sourceBoxPos.isPresent() && targetBoxPos.isPresent()) {
            transferItemTask.setSourceBlockPos(sourceBoxPos.get());
            transferItemTask.setTargetBlockPos(targetBoxPos.get());
            transferItemTask.startTransferItem();
        }
    }

    public void stopTransferItems() {
        transferItemTask.stopTransferItem();
    }


    static class TransferItemTask {
        public static final Logger LOGGER = LogManager.getLogger("TransferItemTask");

        private MinecraftClient client;
        private ScheduledExecutorService executor;

        private XrayRender xrayRender;
        private PlayerMotion playerMotion;

        private BlockPos sourceBlockPos;
        private BlockPos targetBlockPos;

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

        public void setSourceBlockPos(BlockPos blockPos) {
            this.sourceBlockPos = blockPos;
        }

        public void setTargetBlockPos(BlockPos blockPos) {
            this.targetBlockPos = blockPos;
        }

        public void startTransferItem() {
            if (future == null || future.isDone()) {
                future = executor.schedule(this::doTransferItem, 1000, TimeUnit.MILLISECONDS);
            }
        }

        public void stopTransferItem() {
            future.cancel(true);
        }

        public void doTransferItem() {
            HotPlugMouse.unplugMouse(client);
            try {
                while (!future.isCancelled()) {
                    moveToNearBlock(sourceBlockPos);
                    client.execute(() -> client.currentScreen.close());
                    Thread.sleep(100);

                    client.options.useKey.setPressed(true);
                    Thread.sleep(100);
                    client.options.useKey.setPressed(false);
                    Thread.sleep(100);

                    if (!(client.currentScreen instanceof GenericContainerScreen)) {
                        throw new RuntimeException("Screen not open");
                    }

                    int bagOffset = InventoryManager.getBagOffset(client);
                    List<Slot> itemsInBox = InventoryManager.selectAllItemInBox(client);
                    List<Slot> bagSlots = InventoryManager.selectAllEmptyInBag(client);
                    LOGGER.info(itemsInBox);

                    InventoryManager.transferSlots(client, itemsInBox, bagSlots);
                    Thread.sleep(500);

                    client.execute(() -> client.currentScreen.close());

                    moveToNearBlock(targetBlockPos);
                    client.execute(() -> client.currentScreen.close());
                    Thread.sleep(100);

                    client.options.useKey.setPressed(true);
                    Thread.sleep(100);
                    client.options.useKey.setPressed(false);
                    Thread.sleep(100);

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
            } finally {
                walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
                playerMotion.cancelAllTasks();
                client.options.useKey.setPressed(false);
                HotPlugMouse.plugMouse(client);
                LOGGER.info("Transfer task Finish");
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
    }
}
