package net.fabricmc.example;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Optional;

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

            Quaternion cameraDirection = client.gameRenderer.getCamera().getRotation();
            double fov = client.options.getFov().getValue();
            double angleSize = fov / height;

            Matrix4f matrix4f = Matrix4f.viewboxMatrix(fov, aspect, 0.05f,
                    client.options.getViewDistance().getValue() * 4.0f);

            Vector4f centerPoint =
                    new Vector4f(new Vec3f(Vec3d.ofCenter(blockPos).subtract(client.cameraEntity.getPos())));
            //centerPoint = centerPoint(cameraDirection);
            centerPoint.rotate(cameraDirection);



            client.player.sendMessage(Text.literal("%s %f %f".formatted(cameraDirection, fov, angleSize)));
            client.player.sendMessage(Text.literal("%d, %d, %f".formatted(width, height, aspect)));
            client.player.sendMessage(Text.literal("%s".formatted(matrix4f)));
            client.player.sendMessage(Text.literal("%s".formatted(centerPoint)));

        }
    }


}
