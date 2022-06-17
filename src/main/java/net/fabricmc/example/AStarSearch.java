package net.fabricmc.example;

import com.google.common.collect.Lists;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class AStarSearch {
    private final MinecraftClient client;
    private final BlockPos start;
    private final BlockPos end;

    public AStarSearch(MinecraftClient client, BlockPos start, BlockPos end) {
        this.client = client;
        this.start = start;
        this.end = end;
    }

    public static Optional<BlockPos> verticalFindFloor(ClientWorld world, BlockPos blockPos, int lowest, int highest) {
        for (int i = highest; i >= lowest; i--) {
            boolean isFloor = isFloor(world, blockPos.add(0, i, 0));
            if (isFloor) {
                return Optional.of(blockPos.add(0, i, 0));
            }
        }
        return Optional.empty();
    }

    public static List<BlockPos> findNearFloor(ClientWorld world, BlockPos centerBlock, int distance) {
        return BlockPos.streamOutwards(centerBlock, distance, 1, distance)
                .map(BlockPos::toImmutable)
                .map((blockPos) -> verticalFindFloor(world, blockPos, -1, 2))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public static Optional<BlockPos> findNearestOne(List<BlockPos> blockPos, Vec3d fromPoint) {
        return blockPos.stream()
                .min(Comparator.comparingDouble(b -> fromPoint.squaredDistanceTo(Vec3d.ofBottomCenter(b))));
    }



    public static boolean isFloor(ClientWorld world, BlockPos blockPos) {
        boolean ret = false;

        List<BlockPos> verticalBlocks = List.of(
                blockPos.down(),
                blockPos,
                blockPos.up(1),
                blockPos.up(2)
        );
        List<BlockState> vertStates = verticalBlocks.stream().map(world::getBlockState).toList();

        if (vertStates.get(0).isSolidBlock(world, verticalBlocks.get(0))
                && vertStates.get(1).canPathfindThrough(world, verticalBlocks.get(1), NavigationType.AIR)
                && vertStates.get(2).canPathfindThrough(world, verticalBlocks.get(1), NavigationType.AIR)) {
            ret = true;
        }

        if (ret) {
            return ret;
        }

        if (vertStates.get(1).isOf(Blocks.LILY_PAD)
                && vertStates.get(2).isAir()
                && vertStates.get(3).isAir()) {
            ret = true;
        }

        return ret;
    }

    public static boolean canPassThrough(ClientWorld world, BlockPos from, BlockPos to) {

        boolean ret = true;

        // 往上跳，檢查頂頭
        if (to.getY() - from.getY() > 0) {
            BlockPos blockAtHead = from.up(2);
            ret = world.getBlockState(blockAtHead).canPathfindThrough(world, blockAtHead, NavigationType.AIR);
        }

        //  往下跳，檢查往下跳的路上都有空格
        if (to.getY() - from.getY() < 0) {
            for (int i = to.getY(); i <= from.getY() + 1; ++i) {
                BlockPos blockPos = to.withY(i);
                ret &= world.getBlockState(blockPos).canPathfindThrough(world, blockPos, NavigationType.AIR);
                if (!ret) {
                    break;
                }
            }
        }
        return ret;
    }

    public Optional<WalkPath> search() {
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
            WalkPath reversedPath = new WalkPath();
            reversedPath.add(end);

            Optional<BlockPos> fromBlock = cameFrom.get(end);
            while (fromBlock.isPresent()) {
                reversedPath.add(fromBlock.get());
                fromBlock = cameFrom.get(fromBlock.get());
            }

            return Optional.of(WalkPath.of(Lists.reverse(reversedPath)));
        }
    }

    public double heuristic(BlockPos b1, BlockPos b2) {
        BlockPos diff = b1.subtract(b2);
        int[] nums = new int[]{MathHelper.abs(diff.getX()), MathHelper.abs(diff.getY()),
                MathHelper.abs(diff.getZ())};
        Arrays.sort(nums);
        for (int i = nums.length - 1; i > 0; i--) {
            nums[i] -= nums[i - 1];
        }
        return nums[0] * MathHelper.sqrt(3) + nums[1] * MathHelper.sqrt(2) + nums[2];
    }

    public double getCostOf(BlockPos b1, BlockPos b2) {
        return MathHelper.sqrt((float) b1.getSquaredDistance(b2));
    }

    public List<BlockPos> findNeighbors(BlockPos blockPos) {
        Optional<BlockPos>[] floors = new Optional[]{
                verticalFindFloor(client.world, blockPos.add(-1, 0, -1), -3, 1),
                verticalFindFloor(client.world, blockPos.add(-1, 0, 0), -3, 1),
                verticalFindFloor(client.world, blockPos.add(-1, 0, 1), -3, 1),
                verticalFindFloor(client.world, blockPos.add(0, 0, -1), -3, 1),
                Optional.of(blockPos),
                verticalFindFloor(client.world, blockPos.add(0, 0, 1), -3, 1),
                verticalFindFloor(client.world, blockPos.add(1, 0, -1), -3, 1),
                verticalFindFloor(client.world, blockPos.add(1, 0, 0), -3, 1),
                verticalFindFloor(client.world, blockPos.add(1, 0, 1), -3, 1)
        };

        List<BlockPos> ret = new ArrayList<>();

        // 前後左右的方塊
        for (int i : new int[]{1, 3, 5, 7}) {
            if (floors[i].isPresent() && canPassThrough(client.world, blockPos, floors[i].get())) {
                ret.add(floors[i].get());
            }
        }

        // 斜角方塊
        Triple<Integer, Integer, Integer>[] corners = new Triple[]{
                Triple.of(0, 1, 3),
                Triple.of(2, 1, 5),
                Triple.of(6, 3, 7),
                Triple.of(8, 5, 7)
        };

        for (var corner : corners) {
            Optional<BlockPos> cor = floors[corner.getLeft()];
            Optional<BlockPos> side1 = floors[corner.getMiddle()];
            Optional<BlockPos> side2 = floors[corner.getRight()];

            if (cor.isPresent() && side1.isPresent() && side2.isPresent() && canPassThrough(client.world, blockPos,
                    cor.get())) {
                BlockPos b = cor.get();
                if (b.getY() - blockPos.getY() > 0) {
                    // 高一格的話，可以跳斜角方塊
                    ret.add(b);
                } else {
                    // 斜角方塊同樣高或是較低，則那兩邊方塊也要跟我一樣高或較低
                    if (side1.get().getY() - blockPos.getY() <= 0
                            && side2.get().getY() - blockPos.getY() <= 0) {
                        ret.add(b);
                    }
                }
            }
        }

        return ret;
    }

    private static class PriBlockPos implements Comparable<PriBlockPos> {
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

