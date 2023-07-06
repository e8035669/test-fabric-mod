package net.fabricmc.example;

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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.source.BiomeAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

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

    private InformationOverlay informationOverlay = new InformationOverlay();

    private XrayRender xrayRender = new XrayRender();

    private PlayerMotion playerMotion = null;
    private boolean isPrintInformations = false;
    private Optional<WalkPath> paths = Optional.empty();
    private Optional<WalkPathRender> walkPathRender = Optional.empty();

    private InventoryManager inventoryManager = new InventoryManager();

    private PlayerMotionManager playerMotionManager;

    private ProjectionUtils projectionUtils;

    private EntityIndicateOverlay entityIndicateOverlay = new EntityIndicateOverlay();

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
                    .then(literal("plug").executes(CommandHelper.wrap(HotPlugMouse::plugMouse)))
                    .then(literal("unplug").executes(CommandHelper.wrap(HotPlugMouse::unplugMouse)))
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

            dispatcher.register(playerMotionManager.registerCommand(literal("manager")));
            dispatcher.register(literal("overlay")
                    .then(argument("type", new ShowModeArgumentType())
                            .executes(context -> {
                                InformationOverlay.ShowMode showMode = context.getArgument("type",
                                        InformationOverlay.ShowMode.class);
                                informationOverlay.setShowMode(showMode);
                                return 1;
                            })));
            dispatcher.register(literal("project").executes(projectionUtils::executeProject));
            dispatcher.register(entityIndicateOverlay.registerCommand(literal("indicator")));
        }));


        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            runningTasks.forEach(task -> {
                task.onRendering(context);
            });
            outlineEntityTasks.forEach(task -> task.onRendering(context));
        });

        playerMotion = new PlayerMotion(MinecraftClient.getInstance(), executor);
        // executor.scheduleAtFixedRate(() -> playerMotion.tick(), 0, 10, TimeUnit.MILLISECONDS);
        // ClientTickEvents.END_CLIENT_TICK.register(client -> playerMotion.tick());
        executor.scheduleAtFixedRate(() -> this.isPrintInformations = true, 0, 5000, TimeUnit.MILLISECONDS);
        // executor.scheduleAtFixedRate(inventoryManager::tick, 0, 1000, TimeUnit.MILLISECONDS);

        HudRenderCallback.EVENT.register(informationOverlay);
        WorldRenderEvents.END.register(xrayRender);
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

        playerMotionManager = new PlayerMotionManager(
                MinecraftClient.getInstance(), executor, xrayRender
        );
        projectionUtils = new ProjectionUtils(xrayRender);

        HudRenderCallback.EVENT.register(entityIndicateOverlay);
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
            Optional<BlockPos> endPos = AStarSearch.verticalFindFloor(client.world, start.add(x, y, z), -30, 30);
            BlockPos end = endPos.orElseGet(() -> start.add(x, y, z));

            AStarSearch aStarSearch = new AStarSearch(client, start, end);
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
                walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
                paths = Optional.of(list);
                walkPathRender = Optional.of(new WalkPathRender(list, 0xA0FFFFFF));
                xrayRender.getRenderables().add(walkPathRender.get());
                player.sendMessage(Text.of("Path founded"), false);
            } else {
                walkPathRender.ifPresent(xrayRender.getRenderables()::remove);
                paths = Optional.empty();
                walkPathRender = Optional.empty();
                player.sendMessage(Text.of("Path not found"), false);
            }
        });
        return 1;
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
        // RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder vertexConsumer = tessellator.getBuffer();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
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
        // RenderSystem.enableTexture();
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

}
