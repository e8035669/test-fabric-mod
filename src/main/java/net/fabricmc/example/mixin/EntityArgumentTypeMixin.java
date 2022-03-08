package net.fabricmc.example.mixin;

import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityArgumentType.class)
public class EntityArgumentTypeMixin {

    @Redirect(
            //method = "listSuggestions(Lcom/mojang/brigadier/context/CommandContext;," +
            //"Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;",
            method = "listSuggestions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/CommandSource;hasPermissionLevel(I)Z"))
    private boolean injectListSuggestions(CommandSource commandSource, int level) {
        return true;
    }
}
