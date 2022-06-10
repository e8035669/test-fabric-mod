package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExampleMod implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LogManager.getLogger("modid");

    // public static final Item FABRIC_ITEM =
    //       new FabricItem(new FabricItemSettings().group(ItemGroup.MISC));

    // public static final Block EXAMPLE_BLOCK =
    //        new ExampleBlock(FabricBlockSettings.of(Material.METAL)
    //        .strength(4.0f).requiresTool());


    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        /*
        LOGGER.info("Hello Fabric world!");

        Registry.register(
                Registry.ITEM,
                new Identifier("tutorial", "fabric_item"),
                FABRIC_ITEM);

        Registry.register(
                Registry.BLOCK,
                new Identifier("tutorial", "example_block"),
                EXAMPLE_BLOCK);

        Registry.register(Registry.ITEM, new Identifier("tutorial",
                "example_block"), new BlockItem(EXAMPLE_BLOCK,
                new FabricItemSettings().group(ItemGroup.MISC)));

        AttackBlockCallback.EVENT.register(new AttackBlockAction());
         */
    }
}
