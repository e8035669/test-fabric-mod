package net.fabricmc.example;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AttackBlockAction implements AttackBlockCallback {
    public static final Logger LOGGER = LogManager.getLogger("modid");


    @Override
    public ActionResult interact(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {

        BlockState state = world.getBlockState(pos);
            /* Manual spectator check is necessary because AttackBlockCallbacks
               fire before the spectator check */
        if (state.isToolRequired() && !player.isSpectator() &&
                player.getMainHandStack().isEmpty())
        {
            player.damage(DamageSource.GENERIC, 1.0F);
            LOGGER.info("AttackBlockTriggered " + state);

        }
        return ActionResult.PASS;
    }
}
