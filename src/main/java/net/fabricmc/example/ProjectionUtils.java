package net.fabricmc.example;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class ProjectionUtils {

    private XrayRender xrayRender;
    private XrayRenderable renderable;


    public ProjectionUtils(XrayRender xrayRender) {
        this.xrayRender = xrayRender;
        this.renderable = null;
    }




    public int executeProject(CommandContext<FabricClientCommandSource> context) {
        showEntityPosition(context.getSource().getClient());
        return 1;
    }

    public void showEntityPosition(MinecraftClient client) {
        if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
            BlockPos blockPos = hitResult.getBlockPos();

            xrayRender.remove(renderable);
            renderable = new CuboidRender(blockPos, 0xFFFF0000);
            xrayRender.add(renderable);

            client.player.sendMessage(Text.literal("%s".formatted(blockPos)));

            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            float aspect = (float) width / height;
            double fov = client.options.getFov().getValue();
            double angleSize = fov / height;

            Camera camera = client.gameRenderer.getCamera();

            Quaternion cameraDirection = camera.getRotation();
            cameraDirection.conjugate();
            Vec3d cameraPos = camera.getPos();

            Vec3f result = new Vec3f(cameraPos.subtract(Vec3d.ofCenter(blockPos)));
            result.transform(new Matrix3f(cameraDirection));

            float half_height = height / 2.0f;
            float scale_factor =
                    (float) (half_height / result.getZ() * Math.tan(MathHelper.RADIANS_PER_DEGREE * fov / 2));

            result.multiplyComponentwise(-scale_factor, scale_factor, 1);

            client.player.sendMessage(Text.literal("%s %f %f".formatted(cameraDirection, fov, angleSize)));
            client.player.sendMessage(Text.literal("%d, %d, %f".formatted(width, height, aspect)));
            client.player.sendMessage(Text.literal("%s".formatted(result)));

        }
    }


}
