package net.fabricmc.example;

import com.google.common.collect.Streams;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.util.*;

public class BooksSortSearching extends Searching<BooksSortSearching.BookSortStatus> {
    public static final Logger LOGGER = LogManager.getLogger("BooksSortSearching");

    private final int[] movingBuffer;
    private final List<BlockPos> currentSelectedBoxes;
    private final Map<BlockPos, List<Slot>> books;
    private final Map<BlockPos, List<Slot>> emptySlots;
    private final Map<Slot, Double> bookScores;
    private final List<Double> sortedScores;

    private Map<BlockPos, Integer> emptySlotsOffset;
    private BookSortStatus start;
    private BookSortStatus end;


    public BooksSortSearching(int[] movingBuffer,
                              List<BlockPos> currentSelectedBoxes,
                              Map<BlockPos, List<Slot>> books,
                              Map<BlockPos, List<Slot>> emptySlots,
                              Map<Slot, Double> bookScores,
                              List<Double> sortedScores) {
        this.movingBuffer = movingBuffer;
        this.currentSelectedBoxes = currentSelectedBoxes;
        this.books = books;
        this.emptySlots = emptySlots;
        this.bookScores = bookScores;
        this.sortedScores = sortedScores;
        this.emptySlotsOffset = new HashMap<>();
        int offset = 0;
        for (BlockPos blockPos : this.currentSelectedBoxes) {
            this.emptySlotsOffset.put(blockPos, offset);
            offset += this.emptySlots.get(blockPos).size();
        }


        List<Optional<Double>> startStatus = new ArrayList<>();
        for (BlockPos p : currentSelectedBoxes) {
            List<Slot> emptyOrBookSlots = emptySlots.get(p);
            for (Slot s : emptyOrBookSlots) {
                if (s.getStack().isEmpty()) {
                    startStatus.add(Optional.empty());
                } else {
                    double score = bookScores.get(s);
                    startStatus.add(Optional.of(score));
                }
            }
        }
        List<Optional<Double>> emptyBufferStatus = new ArrayList<>();
        for (int i = 0; i < this.movingBuffer.length; ++i) {
            emptyBufferStatus.add(Optional.empty());
        }

        List<Optional<Double>> endStatus = new ArrayList<>();
        for (int i = 0; i < startStatus.size(); ++i) {
            if (i < sortedScores.size()) {
                endStatus.add(Optional.of(sortedScores.get(i)));
            } else {
                endStatus.add(Optional.empty());
            }
        }
        this.start = new BookSortStatus(startStatus, emptyBufferStatus);
        this.end = new BookSortStatus(endStatus, emptyBufferStatus);
    }

    public Optional<List<BookSortStatus>> search() {

        LOGGER.info("Start " + start);
        LOGGER.info("End " + end);

        return super.search(start, end);
    }

    @Override
    public double heuristic(BookSortStatus node1, BookSortStatus node2) {
        double ret = 0.0;
        if (node1.currentScoreStatus.size() != node2.currentScoreStatus.size()) {
            throw new RuntimeException("Expect same size %d vs %d".formatted(
                    node1.currentScoreStatus.size(), node2.currentScoreStatus.size()));
        }
        for (int i = 0; i < node1.currentScoreStatus.size(); ++i) {
            Optional<Double> score1 = node1.currentScoreStatus.get(i);
            Optional<Double> score2 = node2.currentScoreStatus.get(i);
            if (!score1.equals(score2)) {
                ret++;
            }
        }
        return ret;
    }

    @Override
    public Iterable<BookSortStatus> findNeighbors(BookSortStatus node) {
        List<BookSortStatus> ret = new ArrayList<>();
        for (BlockPos b : this.currentSelectedBoxes) {
            if (Objects.nonNull(node.selectedBox) && node.selectedBox.equals(b)) {
                continue;
            }
            LOGGER.info("Generate new neighbor");
            List<Optional<Double>> currentScoreStatus = new ArrayList<>(node.currentScoreStatus);
            List<Pair<Integer, Integer>> boxSwitchAction = new ArrayList<>();
            int offset = this.emptySlotsOffset.get(b);
            int length = this.emptySlots.get(b).size();
            LOGGER.info("offset = {}, length = {}", offset, length);
            List<Optional<Double>> statusView = currentScoreStatus.subList(offset, offset + length);
            List<Optional<Double>> targetView = this.end.currentScoreStatus.subList(offset, offset + length);

            for (int i = 0; i < statusView.size(); ++i) {
                Optional<Double> target = targetView.get(i);
                Optional<Double> current = statusView.get(i);
                if (target.isEmpty() || target.equals(current)) {
                    continue;
                }
                // target != current
                for (int j = 0; j < statusView.size(); ++j) {
                    Optional<Double> switchCurrent = statusView.get(j);
                    if (switchCurrent.isEmpty()) {
                        continue;
                    }
                    if (!switchCurrent.equals(target)) {
                        continue;
                    }
                    Optional<Double> switchTarget = targetView.get(j);
                    if (switchTarget.equals(switchCurrent)) {
                        continue;
                    }
                    // take j to i
                    boxSwitchAction.add(new Pair<>(j, i));
                    Optional<Double> tmp = statusView.get(j);
                    statusView.set(j, statusView.get(i));
                    statusView.set(i, tmp);
                    break;
                }
            }

            List<Optional<Double>> bufferStatus = new ArrayList<>(node.currentBufferStatus);
            List<Pair<Integer, Integer>> bufferSwitchAction = new ArrayList<>();

            for (int i = 0; i < bufferStatus.size(); ++i) {
                Optional<Double> item = bufferStatus.get(i);
                if (item.isEmpty()) {
                    continue;
                }
                for (int j = 0; j < targetView.size(); ++j) {
                    Optional<Double> target = targetView.get(j);
                    if (target.isEmpty() || !item.equals(target)) {
                        continue;
                    }
                    Optional<Double> current = statusView.get(j);
                    if (!current.equals(target)) {
                        // swap from bag to box
                        bufferSwitchAction.add(new Pair<>(i, j));
                        Optional<Double> tmp = bufferStatus.get(i);
                        bufferStatus.set(i, statusView.get(j));
                        statusView.set(j, tmp);
                        break;
                    }
                }
            }

            for (int i = 0; i < statusView.size(); ++i) {
                Optional<Double> target = targetView.get(i);
                Optional<Double> current = statusView.get(i);
                if (current.isEmpty() || target.equals(current)) {
                    continue;
                }
                for (int j = 0; j < bufferStatus.size(); ++j) {
                    if (bufferStatus.get(j).isPresent()) {
                        continue;
                    }
                    // from box to bag -> swap from bag to box
                    bufferSwitchAction.add(new Pair<>(j, i));
                    Optional<Double> tmp = bufferStatus.get(j);
                    bufferStatus.set(j, statusView.get(i));
                    statusView.set(i, tmp);
                    break;
                }
            }

            BookSortStatus newStatus = new BookSortStatus(currentScoreStatus, bufferStatus,
                    b, boxSwitchAction, bufferSwitchAction);
            ret.add(newStatus);

            LOGGER.info(newStatus);
        }
        return ret;
    }

    @Override
    public double getCostOf(BookSortStatus node1, BookSortStatus node2) {
        return 100;
    }


    public static class BookSortStatus implements SearchNode {
        public List<Optional<Double>> currentScoreStatus;
        public List<Optional<Double>> currentBufferStatus;

        @Nullable
        public BlockPos selectedBox;
        @Nullable
        public List<Pair<Integer, Integer>> boxSwitchAction;
        @Nullable
        public List<Pair<Integer, Integer>> bufferSwitchAction;

        public BookSortStatus(List<Optional<Double>> currentScoreStatus, List<Optional<Double>> currentBufferStatus) {
            this.currentScoreStatus = currentScoreStatus;
            this.currentBufferStatus = currentBufferStatus;
            this.selectedBox = null;
            this.boxSwitchAction = null;
            this.bufferSwitchAction = null;
        }

        public BookSortStatus(
                List<Optional<Double>> currentScoreStatus,
                List<Optional<Double>> currentBufferStatus,
                @Nullable BlockPos selectedBox,
                @Nullable List<Pair<Integer, Integer>> boxSwitchAction,
                @Nullable List<Pair<Integer, Integer>> bufferSwitchAction) {
            this.currentScoreStatus = currentScoreStatus;
            this.currentBufferStatus = currentBufferStatus;
            this.selectedBox = selectedBox;
            this.boxSwitchAction = boxSwitchAction;
            this.bufferSwitchAction = bufferSwitchAction;
        }


        @Override
        public String toString() {
            return "BookSortStatus{" +
                    "currentScoreStatus=" + currentScoreStatus +
                    ", currentBufferStatus=" + currentBufferStatus +
                    ", selectedBox=" + selectedBox +
                    ", boxSwitchAction=" + boxSwitchAction +
                    ", bufferSwitchAction=" + bufferSwitchAction +
                    '}';
        }
    }
}
