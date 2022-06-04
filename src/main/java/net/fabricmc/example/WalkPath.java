package net.fabricmc.example;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class WalkPath extends ArrayList<BlockPos> {

    public static WalkPath of(List<BlockPos> other) {
        WalkPath walkPath = new WalkPath();
        walkPath.addAll(other);
        return walkPath;
    }
}
