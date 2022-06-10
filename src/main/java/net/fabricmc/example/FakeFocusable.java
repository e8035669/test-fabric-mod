package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;

public interface FakeFocusable {
    static FakeFocusable of(MinecraftClient client) {
        return (FakeFocusable) client;
    }

    void setFakeFocus(boolean isFakeFocus);
}
