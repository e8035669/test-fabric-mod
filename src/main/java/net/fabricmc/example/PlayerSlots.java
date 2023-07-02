package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.List;

public class PlayerSlots {

    public Slot craftingResult;
    public List<Slot> craftingInputs;
    public List<Slot> equipmentSlots;
    public List<Slot> bagSlots;
    public List<Slot> shortcutSlots;
    public Slot offhandSlot;
    public List<Slot> allSlots;

    public PlayerSlots(PlayerScreenHandler handler) {
        List<Slot> slots = handler.slots;

        craftingResult = slots.get(0);
        craftingInputs = slots.subList(1, 1 + 4);
        equipmentSlots = slots.subList(5, 5 + 4);
        bagSlots = slots.subList(9, 9 + 3 * 9);
        shortcutSlots = slots.subList(36, 36 + 9);
        offhandSlot = slots.get(45);
        allSlots = slots;
    }

    public static List<ItemStack> getHotBarItems(MinecraftClient client) {
        return getHotBarItems(client.player.getInventory());
    }

    public static List<ItemStack> getHotBarItems(PlayerInventory playerInventory) {
        return playerInventory.main.subList(0, 9);
    }


}
