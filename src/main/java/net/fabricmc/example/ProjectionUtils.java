package net.fabricmc.example;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

            Quaternionf cameraDirection = camera.getRotation();
            cameraDirection.conjugate();
            Vec3d cameraPos = camera.getPos();

            Vector3f result = cameraPos.subtract(Vec3d.ofCenter(blockPos)).toVector3f();
            cameraDirection.transform(result);

            float half_height = height / 2.0f;
            float scale_factor =
                    (float) (half_height / result.z() * Math.tan(MathHelper.RADIANS_PER_DEGREE * fov / 2));

            result.mul(-scale_factor, scale_factor, 1);

            client.player.sendMessage(Text.literal("%s %f %f".formatted(cameraDirection, fov, angleSize)));
            client.player.sendMessage(Text.literal("%d, %d, %f".formatted(width, height, aspect)));
            client.player.sendMessage(Text.literal("%s".formatted(result)));

        }
    }


}
