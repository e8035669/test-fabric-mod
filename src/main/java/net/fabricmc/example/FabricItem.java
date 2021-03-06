package net.fabricmc.example;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class FabricItem extends Item {

    public FabricItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.playSound(SoundEvents.BLOCK_WOOL_BREAK, 1.0f, 1.0f);
        // user.playSound(SoundEvents.BLOCK_SAND_BREAK, 1.0f, 1.0f);
        // return super.use(world, user, hand);
        // return TypedActionResult.fail(user.eatFood(world, getDefaultStack()));
        // return TypedActionResult.success(user.getStackInHand(hand));
        return TypedActionResult.consume(user.getStackInHand(hand));
    }
}
