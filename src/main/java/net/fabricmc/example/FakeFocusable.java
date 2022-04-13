package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;

public interface FakeFocusable {
    void setFakeFocus(boolean isFakeFocus);

    static FakeFocusable of(MinecraftClient client) {
        return (FakeFocusable) client;
    }
}
