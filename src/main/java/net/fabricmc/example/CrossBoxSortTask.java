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
import net.minecraft.util.Pair;
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
import java.util.stream.Collectors;
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
            this.currentSelectedBoxes.add(blockPos.toImmutable());
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
            future = executor.schedule(() -> this.doSortBooks(Set.of(target.target)), 1000, TimeUnit.MILLISECONDS);
        }
        return Command.SINGLE_SUCCESS;
    }

    public void startSortBoxesAccordHand() {
        ItemStack itemStack = client.player.getInventory().getMainHandStack();
        client.player.sendMessage(itemStack.getItem().getName());
        MutableText texts = Text.literal("Accept target:");
        Set<EnchantmentTarget> acceptedTarget = Arrays.stream(EnchantmentTarget.values())
                .filter(t -> t.isAcceptableItem(itemStack.getItem()))
                .collect(Collectors.toSet());

        for (EnchantmentTarget t : acceptedTarget) {
            texts.append(t.toString()).append(", ");
        }
        client.player.sendMessage(texts);

        if (future == null || future.isDone()) {
            future = executor.schedule(() -> this.doSortBooks(acceptedTarget), 1000, TimeUnit.MILLISECONDS);
        }
    }

    private class SortBooksImpl {
        private CrossBoxSortTask source;
        private Set<EnchantmentTarget> targets;

        public final Logger LOGGER;
        private final MinecraftClient client;
        private final ScheduledExecutorService executor;
        private final XrayRender xrayRender;
        private final XrayRenders renders;
        private final PlayerMotion playerMotion;
        private final ScheduledFuture<?> future;
        private final Optional<WalkPathRender> walkPathRender;
        private final List<BlockPos> currentSelectedBoxes;


        private int[] movingBuffer;
        private Map<BlockPos, List<Slot>> books = new HashMap<>();
        private Map<BlockPos, List<Slot>> emptySlots = new HashMap<>();
        private Map<Slot, Double> bookScores;
        private List<Double> sortedScores;
        private List<BooksSortSearching.BookSortStatus> searchResult;


        public SortBooksImpl(CrossBoxSortTask source, Set<EnchantmentTarget> targets) {
            this.source = source;
            this.targets = targets;
            this.LOGGER = CrossBoxSortTask.LOGGER;
            this.client = source.client;
            this.executor = source.executor;
            this.xrayRender = source.xrayRender;
            this.renders = source.renders;
            this.playerMotion = source.playerMotion;
            this.future = source.future;
            this.walkPathRender = source.walkPathRender;
            this.currentSelectedBoxes = source.currentSelectedBoxes;
        }

        private void sortBooks() throws InterruptedException {
            client.player.sendMessage(Text.literal("Start sort books"));
            this.source.checkStatus();

            this.movingBuffer = this.getMovingBuffer();
            String msg = String.format("I have %d empty slot to move", this.movingBuffer.length);
            client.player.sendMessage(Text.literal(msg));

            msg = "Start check boxes";
            client.player.sendMessage(Text.literal(msg));
            this.collectBooksInBox();
            this.collectBookScores();
            this.printBooksInBoxInfo();

            BooksSortSearching searching = new BooksSortSearching(
                    movingBuffer, currentSelectedBoxes, books, emptySlots, bookScores, sortedScores);
            var result = searching.search();
            if (result.isPresent()) {
                int len = result.get().size();
                client.player.sendMessage(Text.literal("Has result %d steps.".formatted(len)));
            } else {
                client.player.sendMessage(Text.literal("Search failed"));
            }

            if (result.isPresent()) {
                this.searchResult = result.get();
                this.doSortBooks();
            }



            client.player.sendMessage(Text.literal("Finish sort books"));
        }

        private void doSortBooks() throws InterruptedException {
            int len = this.searchResult.size();
            client.player.sendMessage(Text.literal("Start sorting books, %d steps".formatted(len)));

            for (BooksSortSearching.BookSortStatus sortStatus : this.searchResult) {
                CrossBoxSortTask.throwOnCancelled(this.future);
                BlockPos boxPos = sortStatus.selectedBox;
                this.source.moveToNearBlock(boxPos);
                this.source.pressKey(client.options.useKey);
                waitFor(() -> client.currentScreen instanceof GenericContainerScreen,
                        10, 50, true);

                GenericContainerSlots slots = new GenericContainerSlots(client.currentScreen);
                List<Pair<Integer, Integer>> boxSwitchAction = sortStatus.boxSwitchAction;
                for (Pair<Integer, Integer> p : boxSwitchAction) {


                }





            }
        }

        private void collectBooksInBox() throws InterruptedException {
            for (BlockPos blockPos : this.currentSelectedBoxes) {
                CrossBoxSortTask.throwOnCancelled(this.future);
                this.source.moveToNearBlock(blockPos);
                this.source.pressKey(client.options.useKey);
                waitFor(() -> client.currentScreen instanceof GenericContainerScreen,
                        10, 50, true);

                GenericContainerSlots slots = new GenericContainerSlots(client.currentScreen);
                List<Slot> booksInBox = slots.boxSlots.stream()
                        .filter(s -> s.getStack().isOf(Items.ENCHANTED_BOOK))
                        .toList();
                books.put(blockPos, booksInBox);

                List<Slot> emptyInBox = slots.boxSlots.stream()
                        .filter(s -> s.getStack().isEmpty() || s.getStack().isOf(Items.ENCHANTED_BOOK))
                        .toList();
                emptySlots.put(blockPos, emptyInBox);

                client.send(this.source::closeScreen);
                Thread.sleep(500);
            }
            client.player.sendMessage(Text.literal("List books"));
        }

        private void collectBookScores() {
            this.bookScores = this.currentSelectedBoxes.stream()
                    .flatMap(b -> books.get(b).stream())
                    .map(s -> Map.entry(s, scoringBook(targets, EnchantmentHelper.get(s.getStack()))))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            this.sortedScores = bookScores.values().stream()
                    .sorted(Comparator.comparingDouble(Double::doubleValue).reversed())
                    .toList();
        }

        private void printBooksInBoxInfo() {
            for (BlockPos blockPos : this.currentSelectedBoxes) {
                List<Slot> slots = books.get(blockPos);
                List<Slot> emptySlot = emptySlots.get(blockPos);
                client.player.sendMessage(Text.literal("Box %s, %d space".formatted(blockPos, emptySlot.size())));
                for (Slot s : slots) {
                    Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(s.getStack());
                    MutableText text = Text.literal("Slot %d:".formatted(s.getIndex()));
                    for (Map.Entry<Enchantment, Integer> e : enchantments.entrySet()) {
                        text.append(" ").append(e.getKey().getName(e.getValue()));
                    }
                    // double score = scoringBook(targets, enchantments);
                    double score = this.bookScores.get(s);
                    text.append(", score: %.2f".formatted(score));
                    client.player.sendMessage(text);
                }
            }
        }

        private int[] getMovingBuffer() {
            List<ItemStack> mainBag = client.player.getInventory().main;
            int[] movingBuffer = IntStream.range(0, mainBag.size())
                    .filter(i -> mainBag.get(i).isEmpty())
                    .toArray();
            return movingBuffer;
        }

    }


    public void doSortBooks(Set<EnchantmentTarget> targets) {
        try {
            SortBooksImpl impl = new SortBooksImpl(this, targets);
            impl.sortBooks();
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

    private static double scoringBook(Set<EnchantmentTarget> target, Map<Enchantment, Integer> enchantments) {
        double score = 0.0;
        for (Map.Entry<Enchantment, Integer> e : enchantments.entrySet()) {
            if (e.getKey().isCursed()) {
                score = -999;
                break;
            }
            if (target.contains(e.getKey().target)) {
                // 不是最高級扣分
                double level_diff = 5.0 - (e.getKey().getMaxLevel() - e.getValue());
                double weight = 1.0 + 0.1 * e.getKey().getMaxLevel();
                score += level_diff * weight;
            }
        }
        return score;
    }

    private void checkStatus() {
        if (this.currentSelectedBoxes.isEmpty()) {
            throw new RuntimeException("No selected boxes");
        }
        if (client.player.getInventory().getEmptySlot() == -1) {
            throw new RuntimeException("Need at least one empty slot.");
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
                throwOnCancelled(this.future);
                Thread.sleep(10);
            }
            LOGGER.info("Wait for WalkFollowPathTask is finished.");
        }

        Thread.sleep(200);

        playerMotion.lookDirection(Vec3d.ofCenter(sourceBlockPos));

        while (!playerMotion.isTaskEmpty()) {
            throwOnCancelled(this.future);
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

    private static void throwOnCancelled(ScheduledFuture<?> future) {
        if (future.isCancelled()) {
            throw new RuntimeException("Future Cancelled");
        }
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
