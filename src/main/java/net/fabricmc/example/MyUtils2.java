package net.fabricmc.example;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.example.mixin.BiomeAccessMixin;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.tick.Tick;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

public class MyUtils2 {
    public static final Logger LOGGER = LogManager.getLogger("MyUtils2");

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final BlockingQueue<PairedRenderableFuture<?>> runningTasks = new ArrayBlockingQueue<>(100);
    private final BlockingQueue<PairedRenderableFuture<?>> outlineEntityTasks = new ArrayBlockingQueue<>(100);
    private PlayerMotion playerMotion = null;

    public PlayerMotion getPlayerMotion() {
        return playerMotion;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    boolean isPrintInformations = false;

    public void registerCommands() {
        ClientCommandManager.DISPATCHER.register(literal("detectBlock")
                .then(argument("target", BlockStateArgumentType.blockState())
                        .then(argument("range", IntegerArgumentType.integer(1, 999))
                                .executes(this::executeDetectBlock)
                        )
                )
        );
        ClientCommandManager.DISPATCHER.register(literal("stopDetectBlock")
                .executes(context -> {
                    runningTasks.forEach(task-> task.cancel(false));
                    runningTasks.clear();
                    return 1;
                })
        );
        ClientCommandManager.DISPATCHER.register(literal("detectEntities")
                .then(argument("target", EntityArgumentType.entities())
                        .executes(this::executeDetectEntities)
                )
        );
        ClientCommandManager.DISPATCHER.register(literal("stopDetectEntities")
                .executes(this::stopDetectEntities)
        );
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            runningTasks.forEach(task -> {
                task.onRendering(context);
            });
            outlineEntityTasks.forEach(task -> task.onRendering(context));
        });

        ClientCommandManager.DISPATCHER.register(literal("getSeed")
                .executes(this::executeGetSeed)
        );

        ClientCommandManager.DISPATCHER.register(literal("move")
                .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                        .executes(this::executeMove))))
        );

        ClientCommandManager.DISPATCHER.register(literal("openScreen")
                .executes(this::doOpenScreen)
        );

        ClientCommandManager.DISPATCHER.register(literal("mouse")
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
        ClientCommandManager.DISPATCHER.register(literal("tempMove").executes(this::executeTempMove));

        playerMotion = new PlayerMotion(MinecraftClient.getInstance());
        executor.scheduleAtFixedRate(()->playerMotion.tick(), 0, 10, TimeUnit.MILLISECONDS);
        // ClientTickEvents.END_CLIENT_TICK.register(client -> playerMotion.tick());
        executor.scheduleAtFixedRate(() -> this.isPrintInformations = true, 0, 5000, TimeUnit.MILLISECONDS);

        HudRenderCallback.EVENT.register(this::onHudRender);
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
        long seed = ((BiomeAccessMixin)biomeAccess).getSeed();
        context.getSource().sendFeedback(new LiteralText("Biomes Seed: %d".formatted(seed)));
        return 1;
    }

    private int executeMove(CommandContext<FabricClientCommandSource> context) {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        ClientPlayerEntity player = context.getSource().getPlayer();
        Vec3d pos = player.getPos();

        player.setPosition(pos.x + x, pos.y + y, pos.z + z);
        context.getSource().sendFeedback(new LiteralText("Move (%d %d %d)".formatted(x, y, z)));
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
            client.setScreen(new MyTestScreen(new LiteralText("My Test Screen"), client, this));
            client.player.sendMessage(new LiteralText("Set Screen"), false);
        });
        // Thread.sleep(1000);
        // playerMotion.moveForward(100);
        // playerMotion.changeLookDirection(1, 0, 100);
        // playerMotion.changeLookDirection(-1, 0, 100);
        // playerMotion.moveForward(100);
    }



    private int executeTempMove(CommandContext<FabricClientCommandSource> context) {

        executor.schedule(() -> {
            ClientPlayerEntity player = context.getSource().getPlayer();
            Mouse mouse = context.getSource().getClient().mouse;
            Vec3d pos = Vec3d.ofBottomCenter(player.getBlockPos().add(10, 0, 10));
            HotPlugMouse.of(mouse).unplugMouse();
            player.sendMessage(new LiteralText("Move to %s".formatted(pos)), false);
            playerMotion.walkTo(pos);
            playerMotion.send(() -> {
                player.sendMessage(new LiteralText("Move done"), false);
                HotPlugMouse.of(mouse).plugMouse();
            });

        }, 1000, TimeUnit.MILLISECONDS);
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
}
