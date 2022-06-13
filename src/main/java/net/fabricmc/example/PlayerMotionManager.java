package net.fabricmc.example;

import net.fabricmc.example.mixin.MouseMixin;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
        playerMotion = new PlayerMotion(client);
        sourceBoxPos = Optional.empty();
        sourceBoxRender = Optional.empty();
        targetBoxPos = Optional.empty();
        targetBoxRender = Optional.empty();

        executor.scheduleAtFixedRate(() -> playerMotion.tick(), 0, 10, TimeUnit.MILLISECONDS);
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


    class TransferItemTask {
        public static final Logger LOGGER = LogManager.getLogger("TransferItemTask");

        private MinecraftClient client;
        private ScheduledExecutorService executor;

        private XrayRender xrayRender;
        private PlayerMotion playerMotion;

        private BlockPos sourceBlockPos;
        private BlockPos targetBlockPos;

        private ScheduledFuture<?> future;
        private Optional<WalkPathRender> walkPathRender;



        public TransferItemTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender,
                                PlayerMotion playerMotion) {
            this.client = client;
            this.executor = executor;
            this.xrayRender = xrayRender;
            this.playerMotion = playerMotion;
            walkPathRender = Optional.empty();
        }

        public void setSourceBlockPos(BlockPos blockPos) {
            this.sourceBlockPos = blockPos;
        }

        public void setTargetBlockPos(BlockPos blockPos) {
            this.targetBlockPos = blockPos;
        }

        public void startTransferItem() {
            future = executor.schedule(this::doTransferItem, 1000, TimeUnit.MILLISECONDS);
        }

        public void stopTransferItem() {
            future.cancel(false);
        }

        public void doTransferItem() {
            HotPlugMouse.unplugMouse(client);
            try {
                List<BlockPos> blocksNear = AStarSearch.findNearFloor(client.world, sourceBlockPos, 1);
                Optional<BlockPos> nearestBlock = AStarSearch.findNearestOne(blocksNear, client.player.getPos());
                if (nearestBlock.isPresent()) {
                    AStarSearch aStarSearch = new AStarSearch(client, client.player.getBlockPos(), nearestBlock.get());
                    Optional<WalkPath> walkPath = aStarSearch.search();
                    if (walkPath.isPresent()) {
                        playerMotion.walkFollowPath(walkPath.get());
                        walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
                        walkPathRender = Optional.of(new WalkPathRender(walkPath.get(), 0xA0FFFFFF));
                        xrayRender.getRenderables().add(walkPathRender.get());
                    }
                }
                while (!playerMotion.isTaskEmpty()) {
                    Thread.sleep(10);
                }

            } catch (Exception ex) {
                LOGGER.info(ex);
            }

            HotPlugMouse.plugMouse(client);
            LOGGER.info("Transfer task Finish");
        }
    }
}
