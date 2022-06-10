package net.fabricmc.example;

import net.minecraft.client.option.KeyBinding;

public interface KeyPressable {
    static KeyPressable of(KeyBinding keyBinding) {
        return (KeyPressable) keyBinding;
    }

    void onKeyPressed();
}
