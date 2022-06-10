package net.fabricmc.example;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.example.mixin.BiomeAccessMixin;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.biome.source.BiomeAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MyUtils2 {
    public static final Logger LOGGER = LogManager.getLogger("MyUtils2");

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final BlockingQueue<PairedRenderableFuture<?>> runningTasks = new ArrayBlockingQueue<>(100);
    private final BlockingQueue<PairedRenderableFuture<?>> outlineEntityTasks = new ArrayBlockingQueue<>(100);
    private PlayerMotion playerMotion = null;
    private boolean isPrintInformations = false;
    private Optional<WalkPath> paths = Optional.empty();

    private static void drawLine(Vec3d pos1, Vec3d pos2, int color, Vec3d cameraPos, BufferBuilder vertexConsumer,
                                 MatrixStack matrixStack) {
        MatrixStack.Entry entry = matrixStack.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        Vec3f diff = new Vec3f(pos2.subtract(pos1));
        float t =
                MathHelper.sqrt(diff.getX() * diff.getX() + diff.getY() * diff.getY() + diff.getZ() * diff.getZ());

        float a = (float) (pos1.x - cameraPos.x);
        float b = (float) (pos1.y - cameraPos.y);
        float c = (float) (pos1.z - cameraPos.z);

        vertexConsumer.vertex(positionMatrix, a, b, c).color(color).normal(normalMatrix, diff.getX() / t, diff.getY() / t,
                diff.getZ() / t).next();

        float d = (float) (pos2.x - cameraPos.x);
        float e = (float) (pos2.y - cameraPos.y);
        float f = (float) (pos2.z - cameraPos.z);
        //float t = MathHelper.sqrt(d * d + e * e + f * f);

        vertexConsumer.vertex(positionMatrix, d, e, f).color(color).normal(normalMatrix, diff.getX() / t, diff.getY() / t,
                diff.getZ() / t).next();
    }

    public PlayerMotion getPlayerMotion() {
        return playerMotion;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    public void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> {
            dispatcher.register(literal("detectBlock")
                    .then(argument("target", BlockStateArgumentType.blockState(registryAccess))
                            .then(argument("range", IntegerArgumentType.integer(1, 999))
                                    .executes(this::executeDetectBlock)
                            )
                    )
            );
            dispatcher.register(literal("stopDetectBlock")
                    .executes(context -> {
                        runningTasks.forEach(task -> task.cancel(false));
                        runningTasks.clear();
                        return 1;
                    })
            );
            dispatcher.register(literal("detectEntities")
                    .then(argument("target", EntityArgumentType.entities())
                            .executes(this::executeDetectEntities)
                    )
            );
            dispatcher.register(literal("stopDetectEntities")
                    .executes(this::stopDetectEntities)
            );

            dispatcher.register(literal("getSeed")
                    .executes(this::executeGetSeed)
            );

            dispatcher.register(literal("move")
                    .then(argument("x", IntegerArgumentType.integer())
                            .then(argument("y", IntegerArgumentType.integer())
                                    .then(argument("z", IntegerArgumentType.integer())
                                            .executes(this::executeMove))))
            );

            dispatcher.register(literal("openScreen")
                    .executes(this::doOpenScreen)
            );

            dispatcher.register(literal("mouse")
                    .then(literal("plug").executes(context -> {
                        HotPlugMouse mouse = HotPlugMouse.of(context.getSource().getClient().mouse);
                        mouse.plugMouse();
                        return 1;
                    }))
                    .then(literal("unplug").executes(context -> {
                        HotPlugMouse mouse = HotPlugMouse.of(context.getSource().getClient().mouse);
                        mouse.unplugMouse();
                        return 1;
                    }))
            );
            dispatcher.register(literal("autoMove")
                    .then(argument("x", IntegerArgumentType.integer())
                            .then(argument("y", IntegerArgumentType.integer())
                                    .then(argument("z", IntegerArgumentType.integer())
                                            .executes(this::executeAutoMove)))));

            dispatcher.register(literal("autoMove2").executes(this::executeAutomove2));

            dispatcher.register(literal("pathFind")
                    .then(argument("x", IntegerArgumentType.integer())
                            .then(argument("y", IntegerArgumentType.integer())
                                    .then(argument("z", IntegerArgumentType.integer())
                                            .executes(this::executePathFind)))));
        }));


        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            runningTasks.forEach(task -> {
                task.onRendering(context);
            });
            outlineEntityTasks.forEach(task -> task.onRendering(context));
        });

        playerMotion = new PlayerMotion(MinecraftClient.getInstance());
        executor.scheduleAtFixedRate(() -> playerMotion.tick(), 0, 10, TimeUnit.MILLISECONDS);
        // ClientTickEvents.END_CLIENT_TICK.register(client -> playerMotion.tick());
        executor.scheduleAtFixedRate(() -> this.isPrintInformations = true, 0, 5000, TimeUnit.MILLISECONDS);

        HudRenderCallback.EVENT.register(this::onHudRender);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (false) {
                onAfterEntities(context);
                // onBeforeDebugRender2(context);
            }
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if (false) {
                onBeforeDebugRender(context);
            }
        });
        WorldRenderEvents.END.register(context -> {
            if (true) {
                onBeforeDebugRender2(context);
            }
        });
    }

    private int executeDetectBlock(CommandContext<FabricClientCommandSource> context) {
        BlockStateArgument blockState = context.getArgument("target", BlockStateArgument.class);
        int range = IntegerArgumentType.getInteger(context, "range");
        LOGGER.info(("Get argument %s %d").formatted(blockState.getBlockState(), range));

        OutlineBlocksTask outlineBlocksTask = new OutlineBlocksTask(blockState.getBlockState().getBlock(), range);
        outlineBlocksTask.setClient(context.getSource().getClient());

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(outlineBlocksTask, 0, 1, TimeUnit.SECONDS);
        runningTasks.add(new PairedRenderableFuture<>(future, outlineBlocksTask));

        return 1;
    }

    private int executeDetectEntities(CommandContext<FabricClientCommandSource> context) {
        stopDetectEntities(context);

        EntitySelector entitySelector = context.getArgument("target", EntitySelector.class);

        OutlineEntitiesTask outlineEntitiesTask = new OutlineEntitiesTask(entitySelector, context.getSource());
        outlineEntitiesTask.setClient(context.getSource().getClient());
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(outlineEntitiesTask, 0, 1, TimeUnit.SECONDS);
        outlineEntityTasks.add(new PairedRenderableFuture<>(future, outlineEntitiesTask));
        return 1;
    }

    private int stopDetectEntities(CommandContext<FabricClientCommandSource> context) {
        outlineEntityTasks.forEach(task -> task.cancel(false));
        outlineEntityTasks.clear();
        for (Entity entity : context.getSource().getWorld().getEntities()) {
            entity.setGlowing(false);
        }
        return 1;
    }

    private int executeGetSeed(CommandContext<FabricClientCommandSource> context) {
        BiomeAccess biomeAccess = context.getSource().getWorld().getBiomeAccess();
        long seed = ((BiomeAccessMixin) biomeAccess).getSeed();
        context.getSource().sendFeedback(Text.of("Biomes Seed: %d".formatted(seed)));
        return 1;
    }

    private int executeMove(CommandContext<FabricClientCommandSource> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        ClientPlayerEntity player = context.getSource().getPlayer();
        Vec3d pos = player.getPos();

        player.setPosition(pos.x + x, pos.y + y, pos.z + z);
        context.getSource().sendFeedback(Text.of("Move (%d %d %d)".formatted(x, y, z)));
        return 1;
    }

    private int doOpenScreen(CommandContext<FabricClientCommandSource> context) {
        executor.schedule(this::openScreen, 1000, TimeUnit.MILLISECONDS);

        return 1;
    }

    private void openScreen() {
        try {
            openScreen0();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openScreen0() throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> {
            client.setScreen(new MyTestScreen(Text.of("My Test Screen"), client, this));
            client.player.sendMessage(Text.of("Set Screen"), false);
        });
        // Thread.sleep(1000);
        // playerMotion.moveForward(100);
        // playerMotion.changeLookDirection(1, 0, 100);
        // playerMotion.changeLookDirection(-1, 0, 100);
        // playerMotion.moveForward(100);
    }

    private int executeAutomove2(CommandContext<FabricClientCommandSource> context) {
        if (paths.isEmpty()) {
            return 1;
        }

        executor.schedule(() -> {
            ClientPlayerEntity player = context.getSource().getPlayer();
            Mouse mouse = context.getSource().getClient().mouse;
            HotPlugMouse.of(mouse).unplugMouse();

            BlockPos pos = paths.get().get(paths.get().size() - 1);
            player.sendMessage(Text.of("Move to %s".formatted(pos)), false);
            playerMotion.walkFollowPath(paths.get());
            playerMotion.send(() -> {
                player.sendMessage(Text.of("Move done"), false);
                HotPlugMouse.of(mouse).plugMouse();
            });

        }, 1000, TimeUnit.MILLISECONDS);
        return 1;
    }

    public int executeAutoMove(CommandContext<FabricClientCommandSource> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");

        executor.schedule(() -> {
            ClientPlayerEntity player = context.getSource().getPlayer();
            Mouse mouse = context.getSource().getClient().mouse;
            Vec3d pos = Vec3d.ofBottomCenter(player.getBlockPos().add(x, y, z));
            HotPlugMouse.of(mouse).unplugMouse();
            player.sendMessage(Text.of("Move to %s".formatted(pos)), false);
            playerMotion.walkTo(pos);
            playerMotion.send(() -> {
                player.sendMessage(Text.of("Move done"), false);
                HotPlugMouse.of(mouse).plugMouse();
            });

        }, 1000, TimeUnit.MILLISECONDS);

        return 1;
    }

    private int executePathFind(CommandContext<FabricClientCommandSource> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        MinecraftClient client = context.getSource().getClient();

        executor.execute(() -> {
            ClientPlayerEntity player = client.player;
            BlockPos start = player.getBlockPos();
            Optional<BlockPos> endPos = AStarSearch2.verticalFindFloor(client.world, start.add(x, y, z), -30, 30);
            BlockPos end = endPos.orElseGet(() -> start.add(x, y, z));

            AStarSearch2 aStarSearch = new AStarSearch2(client, start, end);
            var result = aStarSearch.search();

            if (result.isPresent()) {
                WalkPath list = result.get();
                // StringBuilder sb = new StringBuilder();
                // sb.append("Path: ");
                // for (BlockPos b : list) {
                //     sb.append("(").append(b.getX())
                //             .append(",").append(b.getY())
                //             .append(",").append(b.getZ())
                //             .append(")");
                //     sb.append("->");
                // }
                paths = Optional.of(list);
                // player.sendMessage(new LiteralText(sb.toString()), false);
                player.sendMessage(Text.of("Path founded"), false);
            } else {
                paths = Optional.empty();
                player.sendMessage(Text.of("Path not found"), false);
            }
        });
        return 1;
    }

    public void onHudRender(MatrixStack matrixStack, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        TextRenderer textRenderer = client.textRenderer;
        int height = client.getWindow().getScaledHeight();
        int width = client.getWindow().getScaledWidth();

        matrixStack.push();

        Vec3d velocity = player.getVelocity();
        BlockPos blockPos = player.getBlockPos();
        String speed = String.format("Spd: %.2f, %.2f, %.2f, (%.2f, %.2f, %.2f)",
                player.forwardSpeed, player.sidewaysSpeed, player.getMovementSpeed(),
                velocity.x, velocity.y, velocity.z);
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, speed, 0, height - 40, 0x90FFFFFF);
        String position = String.format("Pos: (%.2f, %.2f, %.2f)", player.getX(), player.getY(), player.getZ());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, position, 0, height - 30, 0x90FFFFFF);
        String blockPosStr = String.format("BPos: (%d, %d, %d)", blockPos.getX(), blockPos.getY(), blockPos.getZ());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, blockPosStr, 0, height - 20, 0x90FFFFFF);
        String dir = String.format("Dir: (%.2f, %.2f)", MathHelper.wrapDegrees(player.getYaw()), player.getPitch());
        DrawableHelper.drawStringWithShadow(matrixStack, textRenderer, dir, 0, height - 10, 0x90FFFFFF);

        matrixStack.pop();
    }

    public void onAfterEntities(WorldRenderContext context) {
        MatrixStack matrixStack = context.matrixStack();
        Camera camera = context.camera();
        float camX = (float) camera.getPos().x;
        float camY = (float) camera.getPos().y;
        float camZ = (float) camera.getPos().z;

        BlockPos blockPos1 = new BlockPos(0, 0, 0);
        Box box = new Box(blockPos1).expand(0.002).offset(-camX, -camY, -camZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.lineWidth(6.0f);
        RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder vertexConsumer = tessellator.getBuffer();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
        vertexConsumer.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();

        if (paths.isPresent()) {
            List<BlockPos> path = paths.get();

            for (BlockPos b : path) {
                vertexConsumer.vertex(positionMatrix, b.getX() - camX + 0.5f, b.getY() - camY + 0.5f,
                        b.getZ() - camZ + 0.5f).color(0xFFFFFFFF).next();
            }
        }
        tessellator.draw();

        vertexConsumer.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        vertexConsumer.vertex(positionMatrix, 0 - camX, 0 - camY, 0 - camZ).color(0xFFFFFFFF).next();
        vertexConsumer.vertex(positionMatrix, 0 - camX, 1 - camY, 0 - camZ).color(0xFFFFFFFF).next();
        vertexConsumer.vertex(positionMatrix, 0 - camX, 1 - camY, 1 - camZ).color(0xFFFFFFFF).next();
        vertexConsumer.vertex(positionMatrix, 0 - camX, 0 - camY, 1 - camZ).color(0xFFFFFFFF).next();
        vertexConsumer.vertex(positionMatrix, 0 - camX, 0 - camY, 0 - camZ).color(0xFFFFFFFF).next();

        tessellator.draw();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    public void onBeforeDebugRender(WorldRenderContext context) {
        MatrixStack matrixStack = context.matrixStack();
        matrixStack.push();
        MatrixStack.Entry entry = matrixStack.peek();
        Camera camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        VertexConsumerProvider consumers = context.consumers();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        float a = (float) (1 - camX);
        float b = (float) (1 - camY);
        float c = (float) (1 - camZ);
        float d = (float) (2 - camX);
        float e = (float) (2 - camY);
        float f = (float) (2 - camZ);
        float t = MathHelper.sqrt(3);

        consumer.vertex(entry.getPositionMatrix(), a, b, c).color(0xFFFF0000).normal(entry.getNormalMatrix(), 1 / t,
                1 / t, 1 / t).next();
        consumer.vertex(entry.getPositionMatrix(), d, e, f).color(0xFFFF0000).normal(entry.getNormalMatrix(), 1 / t,
                1 / t, 1 / t).next();

        matrixStack.pop();
    }

    public void onBeforeDebugRender2(WorldRenderContext context) {
        MatrixStack matrixStack = context.matrixStack();
        matrixStack.push();
        Camera camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        BlockPos blockPos1 = new BlockPos(0, 0, 0);
        Box box = new Box(blockPos1).expand(0.002).offset(-camX, -camY, -camZ);


        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder vertexConsumer = tessellator.getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
        RenderSystem.disableTexture();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        // RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);

        MatrixStack matrixStack1 = RenderSystem.getModelViewStack();
        matrixStack1.push();
        matrixStack1.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.lineWidth(3.0f);

        vertexConsumer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        Matrix4f posMatrix = matrixStack.peek().getPositionMatrix();
        Matrix3f normMatrix = matrixStack.peek().getNormalMatrix();

        float a = (float) (2.0 - camX);
        float b = (float) (2.0 - camY);
        float c = (float) (2.0 - camZ);
        float d = (float) (3.0 - camX);
        float e = (float) (2.0 - camY);
        float f = (float) (2.0 - camZ);
        float t = MathHelper.sqrt(1);

        vertexConsumer.vertex(posMatrix, a, b, c).color(0xFF0000FF).normal(normMatrix, 1f, 0f, 0f).next();
        vertexConsumer.vertex(posMatrix, d, e, f).color(0xFF0000FF).normal(normMatrix, 1f, 0f, 0f).next();

        drawLine(new Vec3d(2, 2, 2), new Vec3d(3, 2, 2), 0xFF0000FF, camera.getPos(), vertexConsumer, matrixStack);
        drawLine(new Vec3d(2, 2, 2), new Vec3d(2, 3, 2), 0xFF0000FF, camera.getPos(), vertexConsumer, matrixStack);
        drawLine(new Vec3d(3, 3, 2), new Vec3d(2, 3, 2), 0xFF0000FF, camera.getPos(), vertexConsumer, matrixStack);
        drawLine(new Vec3d(3, 3, 2), new Vec3d(3, 2, 2), 0xFF0000FF, camera.getPos(), vertexConsumer, matrixStack);

        VoxelShapes.fullCube().offset(4, 2, 2).forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) -> {
            drawLine(new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ),
                    0xFF00FFFF, camera.getPos(), vertexConsumer, matrixStack);
        });


        if (paths.isPresent()) {
            WalkPath path = paths.get();

            for (int i = 0; i < path.size() - 1; ++i) {
                BlockPos pos1 = path.get(i);
                BlockPos pos2 = path.get(i + 1);
                if (pos1.getY() - pos2.getY() == 0) {
                    Vec3d vec1 = Vec3d.ofBottomCenter(pos1);
                    Vec3d vec2 = Vec3d.ofBottomCenter(pos2);
                    drawLine(vec1, vec2, 0xA0FFFFFF, camera.getPos(), vertexConsumer, matrixStack);
                } else {
                    Vec3d vec1 = Vec3d.ofBottomCenter(pos1);
                    Vec3d vec2 = Vec3d.ofBottomCenter(pos2);
                    Vec3d vec3 = new Vec3d((vec1.getX() + vec2.getX()) / 2, vec1.y, (vec1.getZ() + vec2.getZ()) / 2);
                    Vec3d vec4 = new Vec3d((vec1.getX() + vec2.getX()) / 2, vec2.y, (vec1.getZ() + vec2.getZ()) / 2);
                    drawLine(vec1, vec3, 0xA0FFFFFF, camera.getPos(), vertexConsumer, matrixStack);
                    drawLine(vec3, vec4, 0xA0FFFFFF, camera.getPos(), vertexConsumer, matrixStack);
                    drawLine(vec4, vec2, 0xA0FFFFFF, camera.getPos(), vertexConsumer, matrixStack);
                }
            }
        }

        tessellator.draw();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        matrixStack1.pop();
        RenderSystem.applyModelViewMatrix();

        matrixStack.pop();
    }


}
