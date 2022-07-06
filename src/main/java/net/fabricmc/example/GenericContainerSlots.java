package net.fabricmc.example;

import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.List;

public class GenericContainerSlots {
    public List<Slot> playerSlots;
    public List<Slot> bagSlots;

    public GenericContainerSlots(GenericContainerScreenHandler handler) {
        int offset = handler.getRows() * 9;

        bagSlots = handler.slots.subList(0, offset);
        playerSlots = handler.slots.subList(offset, handler.slots.size());
    }
}
