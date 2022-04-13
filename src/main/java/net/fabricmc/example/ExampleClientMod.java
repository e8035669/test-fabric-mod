package net.fabricmc.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
// import org.lwjgl.glfw.GLFW;

public class ExampleClientMod implements ClientModInitializer {

    private class MySettings {
        public boolean lastForward = false;
        public boolean lastBackward = false;
        public boolean lastLeft = false;
        public boolean lastRight = false;
    }

    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(4);


    @Override
    public void onInitializeClient() {
        KeyBinding bindForward =
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "keybinding1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I
                        , "key.category.binding"));
        KeyBinding bindLeft =
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "keybinding2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J
                        , "key.category.binding"));
        KeyBinding bindBackward =
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "keybinding3", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K
                        , "key.category.binding"));
        KeyBinding bindRight =
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "keybinding4", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L
                        , "key.category.binding"));
        KeyBinding bindInfo = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "keybinding5", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P,
                "key.category.binding"
        ));
        KeyBinding bindLeftMouse = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "keybinding6", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O,
                "key.category.binding"
        ));

        MySettings mySettings = new MySettings();

        ClientTickEvents.END_CLIENT_TICK.register((client -> {
            if (bindLeft.isPressed()) {
                client.player.changeLookDirection(-5, 0);
                // client.player.sendMessage(new LiteralText("Left"), false);
            }

            if (bindRight.isPressed()) {
                client.player.changeLookDirection(5, 0);
                // client.player.sendMessage(new LiteralText("Right"), false);
            }

            if (bindForward.isPressed()) {
                client.player.changeLookDirection(0, -5);
            }

            if (bindBackward.isPressed()) {
                client.player.changeLookDirection(0, 5);
            }

            if (bindInfo.wasPressed()) {
                // MyUtils.showTargetBlockInfo(client);
                // client.send(() -> MyUtils.findNearestBlock(client));
                client.mouse.unlockCursor();
            }

            if (bindLeftMouse.wasPressed()) {
                boolean isPressed = client.options.attackKey.isPressed();
                client.player.sendMessage(new LiteralText(
                        String.format("Set attack to %s", !isPressed)), false);
                client.options.attackKey.setPressed(!isPressed);
            }
        }));

        // MyUtils.registerMyCommands();
        // WorldRenderEvents.AFTER_ENTITIES.register(MyUtils::onAfterEntity);

        MyUtils2 myUtils2 = new MyUtils2();
        myUtils2.registerCommands();

    }
}
