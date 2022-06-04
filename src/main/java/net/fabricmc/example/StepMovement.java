package net.fabricmc.example;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

public class StepMovement {
    public BlockPos fromBlock;
    public BlockPos toBlock;
    public int heightDiff;

    public StepMovement(BlockPos fromBlock, BlockPos toBlock) {
        this(fromBlock, toBlock, toBlock.getY() - fromBlock.getY());
    }

    public StepMovement(BlockPos fromBlock, BlockPos toBlock, int heightDiff) {
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
        this.heightDiff = heightDiff;
    }

    public boolean isNeedJump() {
        return heightDiff > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StepMovement that = (StepMovement) o;
        return heightDiff == that.heightDiff && Objects.equals(fromBlock, that.fromBlock) && Objects.equals(toBlock, that.toBlock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromBlock, toBlock, heightDiff);
    }
}
