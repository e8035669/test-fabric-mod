package net.fabricmc.example;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class MoveHelper {

    public static Optional<Float> getTargetPitch(ClientPlayerEntity player, Vec3d pos) {
        double d = pos.x - player.getX();
        double e = pos.y - player.getEyeY();
        double f = pos.z - player.getZ();
        double g = Math.sqrt(d * d + f * f);
        return Math.abs(e) > (double)1.0E-5f || Math.abs(g) > (double)1.0E-5f ?
                Optional.of((float) -Math.toDegrees(MathHelper.atan2(e, g))) :
                Optional.empty();
    }

    public static Optional<Float> getTargetYaw(ClientPlayerEntity player, Vec3d pos) {
        return getTargetYaw(player.getPos(), pos);
    }

    public static Optional<Float> getTargetYaw(Vec3d from, Vec3d target) {
        double d = target.x - from.getX();
        double e = target.z - from.getZ();
        return Math.abs(e) > (double)1.0E-5f || Math.abs(d) > (double)1.0E-5f ?
                Optional.of((float) (Math.toDegrees(MathHelper.atan2(e, d))) - 90.0f) :
                Optional.empty();
    }

    public static float changeAngle(float from, float to, float max) {
        float f = MathHelper.subtractAngles(from, to);
        float g = MathHelper.clamp(f, -max, max);
        return from + g;
    }
}
