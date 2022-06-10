package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.InputUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HotPlugMouse extends Mouse {
    public static final Logger LOGGER = LogManager.getLogger("HotPlugMouse");

    private final MinecraftClient client;
    private long window;
    private boolean isPlugged;

    public HotPlugMouse(MinecraftClient client) {
        super(client);
        this.client = client;
        this.window = 0;
        this.isPlugged = true;
        LOGGER.info("HotPlugMouse is work!");
    }

    public static HotPlugMouse of(Mouse mouse) {
        return (HotPlugMouse) mouse;
    }

    @Override
    public void setup(long window) {
        super.setup(window);
        this.window = window;
        isPlugged = true;
    }

    @Override
    public void lockCursor() {
        if (isPlugged) {
            super.lockCursor();
        }
        // this.client.setScreen(null);
        // this.client.attackCooldown = 10000;
        // this.hasResolutionChanged = true;
        // setResolutionChanged();
    }

    @Override
    public void unlockCursor() {
        if (isPlugged) {
            super.unlockCursor();
        }
    }

    public void plugMouse() {
        isPlugged = true;
        super.setup(this.window);
        FakeFocusable.of(client).setFakeFocus(false);
    }

    public void unplugMouse() {
        isPlugged = false;
        super.unlockCursor();
        InputUtil.setMouseCallbacks(this.window,
                (window, x, y) -> {
                },
                (window, button, action, modifiers) -> {
                },
                (window, offsetX, offsetY) -> {
                },
                (window, count, names) -> {
                }
        );
        FakeFocusable.of(client).setFakeFocus(true);
    }
}
