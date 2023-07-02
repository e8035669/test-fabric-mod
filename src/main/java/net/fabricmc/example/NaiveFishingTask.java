package net.fabricmc.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NaiveFishingTask {
    public static final Logger LOGGER = LogManager.getLogger("TransferItemTask");
    private MinecraftClient client;
    private ScheduledExecutorService executor;

    private XrayRender xrayRender;
    private PlayerMotion playerMotion;

    private ScheduledFuture<?> future;

    private BlockPos bedPosition;
    private CuboidRender bedPosRender;
    private BlockPos boxPosition;
    private CuboidRender boxPosRender;

    private WalkPathRender walkPathRender;

    private BlockPos playerPos;
    private float playerYaw;
    private float playerPitch;

    public NaiveFishingTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender, PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;
        this.future = null;
        this.bedPosition = null;
        this.bedPosRender = null;
        this.boxPosition = null;
        this.boxPosRender = null;
        this.walkPathRender = null;
    }

    public void setBedPosition() {
        HitResult hitResult = client.crosshairTarget;
        boolean isSet = false;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            if (client.world.getBlockState(blockPos).getBlock() instanceof BedBlock) {
                bedPosition = blockPos;
                isSet = true;
                xrayRender.remove(bedPosRender);
                bedPosRender = new CuboidRender(bedPosition, 0xFF00FF00);
                xrayRender.add(bedPosRender);
            }
        }
        if (!isSet) {
            bedPosition = null;
            xrayRender.remove(bedPosRender);
            client.player.sendMessage(Text.literal("Remove bed position"));
        }
    }

    public void setBoxPosition() {
        HitResult hitResult = client.crosshairTarget;
        boolean isSet = false;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            if (client.world.getBlockState(blockPos).isOf(Blocks.CHEST)) {
                boxPosition = blockPos;
                isSet = true;
                xrayRender.remove(boxPosRender);
                boxPosRender = new CuboidRender(boxPosition, 0xFF00FF00);
                xrayRender.add(boxPosRender);
            }
        }
        if (!isSet) {
            boxPosition = null;
            xrayRender.remove(boxPosRender);
            client.player.sendMessage(Text.literal("Remove box position"));
        }
    }

    public void startFishing() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::fishingTask, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopFishing() {
        future.cancel(true);
    }

    private int[] getItemsInBag() throws InterruptedException {
        int[] inventoryHasItem = new int[0];
        if (boxPosition != null) {
            pressKey(client.options.inventoryKey);
            if (!(client.currentScreen instanceof InventoryScreen)) {
                throw new RuntimeException("Inventory not open");
            }
            PlayerSlots playerSlots = new PlayerSlots(((InventoryScreen) client.currentScreen).getScreenHandler());
            inventoryHasItem = playerSlots.allSlots.stream()
                    .filter(slot -> !slot.inventory.getStack(slot.getIndex()).isEmpty())
                    .mapToInt(Slot::getIndex)
                    .toArray();
            client.send(this::closeScreen);
            Thread.sleep(1000);
            LOGGER.info("Remember items in my bag");
        }
        return inventoryHasItem;
    }

    public void fishingTask() {
        HotPlugMouse.unplugMouse(client);
        try {
            int[] inventoryHasItem = getItemsInBag();
            saveCurrentPose();

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

                waitFor(() -> Objects.nonNull(client.player.fishHook), 150, 20, true);
                FishingBobberEntity fishHook = client.player.fishHook;
                Fishable fishable = Fishable.of(fishHook);

                waitFor(() -> fishable.getState() != FishingBobberEntity.State.FLYING || fishHook.isOnGround(),
                        500, 20);

                if (fishable.getState() == FishingBobberEntity.State.HOOKED_IN_ENTITY || fishHook.isOnGround()) {
                    int nextSlot = client.player.getInventory().selectedSlot;
                    nextSlot = (nextSlot + 1) % 9;
                    pressKey(client.options.hotbarKeys[nextSlot]);
                    continue;
                }

                waitFor(fishable::isCaughtFish, 3000, 20);

                pressKey(client.options.useKey, 60);
                Thread.sleep(500);

                // Wait fishhook disappear
                waitFor(() -> Objects.isNull(client.player.fishHook), 150, 20, false);

                boolean isMoveToOtherPlace = false;
                // It is time to sleep
                if ((client.world.getTimeOfDay() % 24000) > 12600 && (client.world.getTimeOfDay() % 24000) < 23400 && Objects.nonNull(bedPosition)) {
                    isMoveToOtherPlace = true;
                    moveToNearBlock(bedPosition);
                    pressKey(client.options.useKey);

                    LOGGER.info("Sleep to wait for morning");
                    while ((client.world.getTimeOfDay() % 24000) > 12600) {
                        Thread.sleep(100);
                    }

                    Thread.sleep(1000);
                }

                // Bag is full
                if (client.player.getInventory().getEmptySlot() == -1 && boxPosition != null) {
                    isMoveToOtherPlace = true;
                    moveToNearBlock(boxPosition);
                    pressKey(client.options.useKey);

                    waitFor(() -> client.currentScreen instanceof GenericContainerScreen,
                            50, 20, false);

                    if (!(client.currentScreen instanceof GenericContainerScreen)) {
                        throw new RuntimeException("Box is not open");
                    }
                    GenericContainerSlots genericContainerSlots =
                            new GenericContainerSlots(((GenericContainerScreen) client.currentScreen).getScreenHandler());

                    LOGGER.info("bag has item");
                    List<Integer> slotids = genericContainerSlots.playerSlots.stream()
                            .filter(slot -> Arrays.stream(inventoryHasItem).noneMatch(value -> slot.getIndex() == value))
                            .map(slot -> slot.id).collect(Collectors.toList());
                    LOGGER.info("transfer slots %s".formatted(slotids));

                    InventoryManager.fastTransferSlots(client, slotids);
                    Thread.sleep(500);
                    client.send(this::closeScreen);
                    Thread.sleep(1000);
                }

                if (isMoveToOtherPlace) {
                    moveBackToPose();
                }
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
                .then(literal("stop").executes(CommandHelper.wrap(this::stopFishing)))
                .then(literal("box").executes(CommandHelper.wrap(this::setBoxPosition)))
                .then(literal("bed").executes(CommandHelper.wrap(this::setBedPosition)));
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

    private void moveBackToPose() throws InterruptedException {
        AStarSearch aStarSearch = new AStarSearch(client, client.player.getBlockPos(), this.playerPos);
        Optional<WalkPath> walkPath = aStarSearch.search();
        if (walkPath.isPresent()) {
            WalkFollowPathTask walkFollowPathTask = new WalkFollowPathTask(client, walkPath.get());
            playerMotion.addTask(walkFollowPathTask);
            xrayRender.remove(walkPathRender);
            walkPathRender = new WalkPathRender(walkPath.get(), 0xA0FFFFFF);
            xrayRender.add(walkPathRender);

            LOGGER.info("Wait for WalkFollowPathTask to finish.");
            while (!walkFollowPathTask.isFinished()) {
                Thread.sleep(10);
            }
            LOGGER.info("Wait for WalkFollowPathTask is finished.");
        }

        LookDirection lookDirection = new LookDirection(client, this.playerPitch, this.playerYaw);
        playerMotion.addTask(lookDirection);

        while (!lookDirection.isFinished()) {
            Thread.sleep(10);
        }

    }

    private void moveToNearBlock(BlockPos sourceBlockPos) throws InterruptedException {
        AStarSearch aStarSearch = new AStarSearch(client, client.player.getBlockPos(), sourceBlockPos);
        aStarSearch.setNearMode(true);
        Optional<WalkPath> walkPath = aStarSearch.search();
        if (walkPath.isPresent()) {
            WalkFollowPathTask walkFollowPathTask = new WalkFollowPathTask(client, walkPath.get());
            playerMotion.addTask(walkFollowPathTask);
            xrayRender.remove(walkPathRender);
            walkPathRender = new WalkPathRender(walkPath.get(), 0xA0FFFFFF);
            xrayRender.add(walkPathRender);

            LOGGER.info("Wait for WalkFollowPathTask to finish.");
            while (!walkFollowPathTask.isFinished()) {
                Thread.sleep(10);
            }
            LOGGER.info("Wait for WalkFollowPathTask is finished.");
        }


//        List<BlockPos> blocksNear = AStarSearch.findNearFloor(client.world, sourceBlockPos, 1);
//        Optional<BlockPos> nearestBlock = AStarSearch.findNearestOne(blocksNear, client.player.getPos());
//        if (nearestBlock.isPresent()) {
//            AStarSearch aStarSearch = new AStarSearch(client, client.player.getBlockPos(), nearestBlock.get());
//            Optional<WalkPath> walkPath = aStarSearch.search();
//            if (walkPath.isPresent()) {
//
//                WalkFollowPathTask walkFollowPathTask = new WalkFollowPathTask(client, walkPath.get());
//                playerMotion.addTask(walkFollowPathTask);
//
//                walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
//                walkPathRender = Optional.of(new WalkPathRender(walkPath.get(), 0xA0FFFFFF));
//                xrayRender.getRenderables().add(walkPathRender.get());
//                LOGGER.info("Wait for WalkFollowPathTask to finish.");
//                while (!walkFollowPathTask.isFinished()) {
//                    Thread.sleep(10);
//                }
//                LOGGER.info("Wait for WalkFollowPathTask is finished.");
//            }
//        }

        Thread.sleep(200);

        playerMotion.lookDirection(Vec3d.ofCenter(sourceBlockPos));

        while (!playerMotion.isTaskEmpty()) {
            Thread.sleep(10);
        }
        Thread.sleep(200);
    }

    private void saveCurrentPose() {
        this.playerPos = client.player.getBlockPos();
        this.playerPitch = client.player.getPitch();
        this.playerYaw = client.player.getYaw();
    }


    private int[] filterItem(List<ItemStack> itemStacks, Item item) {
        return IntStream.range(0, itemStacks.size())
                .filter((i) -> itemStacks.get(i).isOf(item))
                .toArray();
    }

    private void closeScreen() {
        client.setScreen(null);
    }

    private static void waitFor(BooleanSupplier func, int times, int checkInterval) {
        waitFor(func, times, checkInterval, false);
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
}
