package net.fabricmc.example;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericContainerSlots {
    public List<Slot> playerSlots;
    public List<Slot> boxSlots;

    public GenericContainerScreenHandler handler;

    public GenericContainerSlots(Screen screen) {
        this(((GenericContainerScreen) screen).getScreenHandler());
    }

    public GenericContainerSlots(GenericContainerScreenHandler handler) {
        this.handler = handler;
        int offset = handler.getRows() * 9;

        boxSlots = handler.slots.subList(0, offset);
        playerSlots = handler.slots.subList(offset, handler.slots.size());
    }

    public int getSyncId() {
        return handler.syncId;
    }

    public Map<Integer, Integer> getBagIndex2IdMap() {
        Map<Integer, Integer> ret = new HashMap<>();
        for (Slot s : this.playerSlots) {
            ret.put(s.getIndex(), s.id);
        }
        return ret;
    }


}
