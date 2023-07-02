package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class InventoryManager {
    public static final Logger LOGGER = LogManager.getLogger("InventoryManager");


    private Optional<Screen> lastScreen = Optional.empty();

    public InventoryManager() {

    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (lastScreen.isEmpty() && client.currentScreen != null) {
            onScreenOpen(client);
        }

        lastScreen = Optional.ofNullable(client.currentScreen);
    }

    private void onScreenOpen(MinecraftClient client) {
        if (client.currentScreen instanceof HandledScreen<?>) {
            showAllSlots(client);
        }
    }

    private void showAllSlots(MinecraftClient client) {
        HandledScreen<?> screen = (HandledScreen<?>) client.currentScreen;
        ScreenHandler handler = screen.getScreenHandler();

        LOGGER.info("Show all slots in %s".formatted(screen.getTitle()));
        for (Slot slot : handler.slots) {
            LOGGER.info("Slot %d: (%s, %d)".formatted(slot.id, slot.inventory.getClass().getSimpleName(), slot.getIndex()));
        }
    }

    public static List<Slot> selectAllItemInBox(MinecraftClient client) {
        if (checkIfScreenNotOpened(client)) {
            return new ArrayList<>();
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();

        int end = handler.getRows() * 9;
        List<Slot> boxItem = filterSlots(handler.slots, 0, end, slot -> !slot.getStack().isEmpty());

        return boxItem;
    }

    public static List<Slot> selectAllEmptyInBox(MinecraftClient client) {
        if (checkIfScreenNotOpened(client)) {
            return new ArrayList<>();
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();

        int end = handler.getRows() * 9;
        List<Slot> boxItem = filterSlots(handler.slots, 0, end, slot -> slot.getStack().isEmpty());

        return boxItem;
    }

    public static List<Slot> selectAllEmptyInBag(MinecraftClient client) {
        if (checkIfScreenNotOpened(client)) {
            return new ArrayList<>();
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();

        int offset = handler.getRows() * 9;
        int end = offset + 9 * 4;
        List<Slot> emptySlot = filterSlots(handler.slots, offset, end, (slot -> slot.getStack().isEmpty()));

        return emptySlot;
    }

    public static int getBagOffset(MinecraftClient client) {
        if (checkIfScreenNotOpened(client)) {
            return -1;
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();

        int offset = handler.getRows() * 9;
        return offset;
    }


    public static List<Slot> filterSlots(List<Slot> slots, int from, int end, Function<Slot, Boolean> predicate) {
        List<Slot> ret = new ArrayList<>();
        for (int i = from; i < end; ++i) {
            Slot slot = slots.get(i);
            if (predicate.apply(slot)) {
                ret.add(slot);
            }
        }
        return ret;
    }

    public static void transferSlots(MinecraftClient client, List<Slot> fromSlots, List<Slot> toSlots) {
        if (checkIfScreenNotOpened(client)) {
            return;
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();
        for (int i = 0; i < Math.min(fromSlots.size(), toSlots.size()); ++i) {
            Slot fromSlot = fromSlots.get(i);
            Slot toSlot = toSlots.get(i);

            client.interactionManager.clickSlot(
                    handler.syncId, fromSlot.id, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(
                    handler.syncId, toSlot.id, 0, SlotActionType.PICKUP, client.player);
        }
    }

    public static void transferSlotIds(MinecraftClient client, List<Integer> fromSlotIds, List<Integer> toSlotIds) {
        if (checkIfScreenNotOpened(client)) {
            return;
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();
        for (int i = 0; i < Math.min(fromSlotIds.size(), toSlotIds.size()); ++i) {
            Integer fromSlotId = fromSlotIds.get(i);
            Integer toSlotId = toSlotIds.get(i);

            client.interactionManager.clickSlot(
                    handler.syncId, fromSlotId, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(
                    handler.syncId, toSlotId, 0, SlotActionType.PICKUP, client.player);
        }
    }

    public static void fastTransferSlots(MinecraftClient client, List<Integer> slotIds) {
        if (checkIfScreenNotOpened(client)) {
            return;
        }
        GenericContainerScreenHandler handler = ((GenericContainerScreen) client.currentScreen).getScreenHandler();
        for (int slotId : slotIds) {
            client.interactionManager.clickSlot(
                    handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, client.player
            );
        }
    }

    public static boolean checkIfScreenNotOpened(MinecraftClient client) {
        return !(client.currentScreen instanceof GenericContainerScreen);
    }
}
