package net.fabricmc.example;

import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.CallbackI;

import java.util.*;

public class AStarSearch {
    private MinecraftClient client;
    private BlockPos start;
    private BlockPos end;

    public AStarSearch(MinecraftClient client, BlockPos start, BlockPos end) {
        this.client = client;
        this.start = start;
        this.end = end;
    }

    public Optional<List<BlockPos>> search() {
        PriorityQueue<PriBlockPos> frontier = new PriorityQueue<>();
        frontier.add(new PriBlockPos(0, start));
        Map<BlockPos, Optional<BlockPos>> cameFrom = new HashMap<>();
        Map<BlockPos, Double> costSoFar = new HashMap<>();
        cameFrom.put(start, Optional.empty());
        costSoFar.put(start, 0.0);
        boolean isFound = false;

        while (!frontier.isEmpty()) {
            PriBlockPos current = frontier.poll();

            if (current.blockPos.equals(end)) {
                isFound = true;
                break;
            }

            for (BlockPos next : findNeighbors(current.blockPos)) {
                double newCost = costSoFar.get(current.blockPos) + getCostOf(current.blockPos, next);
                if (!cameFrom.containsKey(next) || newCost < costSoFar.get(next)) {
                    costSoFar.put(next, newCost);
                    double priority = newCost + heuristic(next, end);
                    frontier.add(new PriBlockPos(priority, next));
                    cameFrom.put(next, Optional.of(current.blockPos));
                }
            }
        }

        if (!isFound) {
            return Optional.empty();
        } else {
            List<BlockPos> reversedPath = new ArrayList<>();
            reversedPath.add(end);

            Optional<BlockPos> fromBlock = cameFrom.get(end);
            while (fromBlock.isPresent()) {
                reversedPath.add(fromBlock.get());
                fromBlock = cameFrom.get(fromBlock.get());
            }

            return Optional.of(Lists.reverse(reversedPath));
        }
    }

    public double heuristic(BlockPos b1, BlockPos b2) {
        // return b1.getManhattanDistance(b2);
        return MathHelper.square(b1.getSquaredDistance(b2));
    }

    public double getCostOf(BlockPos b1, BlockPos b2) {
        return b1.getManhattanDistance(b2);
        // return MathHelper.square(b1.getSquaredDistance(b2));
    }

    public List<BlockPos> findNeighbors(BlockPos blockPos) {
        List<BlockPos> ret = new ArrayList<>();
        ClientWorld world = client.world;

        List<BlockPos> nearBlocks = List.of(blockPos.north(), blockPos.south(), blockPos.west(), blockPos.east());

        for (BlockPos blockPos1 : nearBlocks) {
            if (isFloor(world, blockPos1)) {
                ret.add(blockPos1);
            }
        }
        return ret;
    }

    public boolean isFloor(ClientWorld world, BlockPos blockPos) {
        boolean ret = true;

        BlockState blockState = world.getBlockState(blockPos);
        if (!blockState.isAir()) {
            ret = false;
        }

        if (ret) {
            BlockPos downBlock = blockPos.down();
            BlockState blockState1 = world.getBlockState(downBlock);
            if (!blockState1.isSolidBlock(world, downBlock)) {
                ret = false;
            }
        }

        return ret;
    }

    private class PriBlockPos implements Comparable<PriBlockPos> {
        public double priority;
        public BlockPos blockPos;

        public PriBlockPos(double priority, BlockPos blockPos) {
            this.priority = priority;
            this.blockPos = blockPos;
        }

        @Override
        public int compareTo(@NotNull PriBlockPos o) {
            return Double.compare(this.priority, o.priority);
        }
    }
}

