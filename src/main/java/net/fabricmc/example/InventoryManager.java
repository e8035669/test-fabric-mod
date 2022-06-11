package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class InventoryManager {
    public static final Logger LOGGER = LogManager.getLogger("InventoryManager");


    private Optional<Screen> lastScreen = Optional.empty();

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (lastScreen.isEmpty() && client.currentScreen != null) {
            onScreenOpen(client);
        }

        lastScreen = Optional.ofNullable(client.currentScreen);
    }

    private void onScreenOpen(MinecraftClient client) {
        if (client.currentScreen instanceof GenericContainerScreen) {
            showAllSlots(client);
        }
    }

    private void showAllSlots(MinecraftClient client) {
        GenericContainerScreen screen = (GenericContainerScreen) client.currentScreen;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        Inventory playerInventory = client.player.getInventory();
        Inventory boxInventory = handler.getInventory();

        LOGGER.info("Sync id: %d".formatted(handler.syncId));
        // for (Slot slot : handler.slots) {
        //     LOGGER.info("Slot %d (%d, %d): %s".formatted(slot.id, slot.x, slot.y, slot.inventory));
        // }

        LOGGER.info("Player Inventory count %d".formatted(playerInventory.size()));
        for (int i = 0; i < playerInventory.size(); ++i) {
            LOGGER.info("Slot %d, %s".formatted(i, playerInventory.getStack(i)));
        }

        LOGGER.info("Box Inventory count %d".formatted(boxInventory.size()));
        for (int i = 0; i < boxInventory.size(); ++i) {
            LOGGER.info("Slot %d, %s".formatted(i, boxInventory.getStack(i)));
        }
    }
}
