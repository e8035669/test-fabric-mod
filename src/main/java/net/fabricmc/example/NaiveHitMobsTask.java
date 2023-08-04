package net.fabricmc.example;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NaiveHitMobsTask {
    public static final Logger LOGGER = LogManager.getLogger("NaiveHitMobsTask");

    private MinecraftClient client;
    private ScheduledExecutorService executor;
    private XrayRender xrayRender;
    private PlayerMotion playerMotion;

    private ScheduledFuture<?> future;

    private int hitInterval = 15000;

    public NaiveHitMobsTask(MinecraftClient client, ScheduledExecutorService executor, XrayRender xrayRender, PlayerMotion playerMotion) {
        this.client = client;
        this.executor = executor;
        this.xrayRender = xrayRender;
        this.playerMotion = playerMotion;
        this.future = null;
    }

    public void startHitMobs() {
        if (future == null || future.isDone()) {
            future = executor.schedule(this::doHitMobs, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stopHitMobs() {
        future.cancel(true);
    }

    private void doHitMobs() {
        try {
            HotPlugMouse.unplugMouse(client);

            long lastHitTime = 0;

            while (!future.isCancelled()) {
                long currTime = client.world.getTime();

                if (currTime - lastHitTime > (this.hitInterval / 50)) {
                    HitResult hitResult = client.crosshairTarget;
                    if (hitResult.getType() == HitResult.Type.ENTITY) {
                        EntityHitResult entityHitResult = (EntityHitResult) hitResult;
                        if (entityHitResult.getEntity() instanceof MobEntity) {
                            pressKey(client.options.attackKey);
                            lastHitTime = currTime;
                        }
                    }
                }

                if (client.player.getHungerManager().isNotFull()) {
                    int weaponSlot = client.player.getInventory().selectedSlot;
                    this.tryEatSafeFoods();
                    Thread.sleep(1000);
                    pressKey(client.options.hotbarKeys[weaponSlot]);
                }

                Thread.sleep(50);
            }

        } catch (InterruptedException ex) {
            LOGGER.info(ex);
            LOGGER.catching(ex);
            Text text =
                    Text.literal(ex.getMessage()).formatted(Formatting.RED);
            client.player.sendMessage(text);
        } finally {
            client.options.attackKey.setPressed(false);
            HotPlugMouse.plugMouse(client);
            LOGGER.info("Attack mob task finish");
            client.player.sendMessage(Text.literal("NaiveHitMobsTask task finish"));
        }
    }

    public int getHitInterval() {
        return this.hitInterval;
    }

    public void setHitInterval(int intervalMs) {
        this.hitInterval = intervalMs;
    }

    private int setHitInterval(CommandContext<FabricClientCommandSource> context) {
        int interval = IntegerArgumentType.getInteger(context, "ms");
        setHitInterval(interval);
        client.player.sendMessage(Text.literal("Set interval to %d ms".formatted(this.hitInterval)));
        return Command.SINGLE_SUCCESS;
    }

    private int getHitInterval(CommandContext<FabricClientCommandSource> context) {
        int interval = this.getHitInterval();
        Text msg = Text.literal("Interval: %d".formatted(interval));
        client.player.sendMessage(msg);
        return Command.SINGLE_SUCCESS;
    }

    private void tryEatSafeFoods() throws InterruptedException {
        List<ItemStack> hotbar = PlayerSlots.getHotBarItems(client);
        OptionalInt foodSlot = IntStream.range(0, 9)
                .filter(i -> hotbar.get(i).isFood())
                .filter(i -> hotbar.get(i).getItem().getFoodComponent().getStatusEffects().isEmpty())
                .findFirst();

        if (foodSlot.isPresent()) {
            pressKey(client.options.hotbarKeys[foodSlot.getAsInt()]);
            // 32 tick * 50 ms/tick = 1600
            pressKey(client.options.useKey, 3000);
        }
    }


    public LiteralArgumentBuilder<FabricClientCommandSource> registerCommand(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return builder
                .then(literal("start").executes(CommandHelper.wrap(this::startHitMobs)))
                .then(literal("stop").executes(CommandHelper.wrap(this::stopHitMobs)))
                .then(literal("interval").executes(this::getHitInterval)
                        .then(argument("ms", IntegerArgumentType.integer(50))
                                .executes(this::setHitInterval)));
    }

    private void pressKey(KeyBinding keyBinding, int sleepTime) throws InterruptedException {
        keyBinding.setPressed(true);
        KeyPressable.of(keyBinding).onKeyPressed();
        Thread.sleep(sleepTime);
        keyBinding.setPressed(false);
    }

    private void pressKey(KeyBinding keyBinding) throws InterruptedException {
        pressKey(keyBinding, 200);
    }


}
