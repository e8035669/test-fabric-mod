package net.fabricmc.example;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;


public class CommandHelper {

    public static Command<FabricClientCommandSource> wrap(Runnable runnable) {
        return new RunnableWrapper<>(runnable);
    }

    public static Command<FabricClientCommandSource> wrap(Consumer<MinecraftClient> consumer) {
        return new ConsumerWrapper(consumer);
    }


    public static class RunnableWrapper<T> implements Command<T> {
        private final Runnable runnable;

        public RunnableWrapper(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public int run(CommandContext<T> context) {
            runnable.run();
            return Command.SINGLE_SUCCESS;
        }
    }

    public static class ConsumerWrapper implements Command<FabricClientCommandSource> {
        private final Consumer<MinecraftClient> runnable;

        public ConsumerWrapper(Consumer<MinecraftClient> runnable) {
            this.runnable = runnable;
        }

        @Override
        public int run(CommandContext<FabricClientCommandSource> context) {
            runnable.accept(context.getSource().getClient());
            return Command.SINGLE_SUCCESS;
        }
    }
}
