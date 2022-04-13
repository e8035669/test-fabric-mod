package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.commons.compress.utils.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class MyTestScreen extends Screen {
    public static final Logger LOGGER = LogManager.getLogger("MyTestScreen");

    private final List<ButtonWidget> buttons = Lists.newArrayList();
    private MinecraftClient client;
    private MyUtils2 myUtils2;

    private ButtonWidget startScriptButton;
    private ButtonWidget2 button2;

    List<MouseKeyPresser> pressers;

    protected MyTestScreen(Text title, MinecraftClient client, MyUtils2 myUtils2) {
        super(title);
        this.client = client;
        this.myUtils2 = myUtils2;
        this.passEvents = true;
        pressers = new ArrayList<>();
    }

    @Override
    protected void init() {
        super.init();
        this.buttons.clear();
        this.buttons.add(this.addDrawableChild(
                new ButtonWidget(this.width / 2 - 150, this.height / 2 - 100, 100, 20,
                        new TranslatableText("jeff.text.test_button"), button -> {
                    this.client.player.sendMessage(new LiteralText("Button click"), false);
                })
        ));
        startScriptButton = new ButtonWidget(this.width / 2 + 50, this.height / 2 - 100, 100, 20,
                new LiteralText("Start Script"), this::startScript);
        this.buttons.add(this.addDrawableChild(startScriptButton));
        button2 = new ButtonWidget2(this.width / 2 - 150, this.height / 2 - 60, 100, 20,
                new LiteralText("Button2"), button -> {
            this.client.player.sendMessage(new LiteralText("On Click"), false);
        }, button -> {
            this.client.player.sendMessage(new LiteralText("On Release"), false);
        });
        this.buttons.add(this.addDrawableChild(button2));

        int anchorX = this.width / 2 + 100;
        int anchorY = this.height / 2 + 30;
        this.buttons.add(this.addDrawableChild(new ButtonWidget2(
                anchorX - 10, anchorY - 10, 20, 20, new LiteralText("˅"),
                button -> {
                    this.client.options.backKey.setPressed(true);
                },
                button -> {
                    this.client.options.backKey.setPressed(false);
                }
        )));
        this.buttons.add(this.addDrawableChild(new ButtonWidget2(
                anchorX - 10, anchorY - 35, 20, 20, new LiteralText("˄"),
                button -> {
                    this.client.options.forwardKey.setPressed(true);
                },
                button -> {
                    this.client.options.forwardKey.setPressed(false);
                }
        )));
        this.buttons.add(this.addDrawableChild(new ButtonWidget2(
                anchorX - 35, anchorY - 10, 20, 20, new LiteralText("˂"),
                button -> this.client.options.leftKey.setPressed(true),
                button -> this.client.options.leftKey.setPressed(false)
        )));
        this.buttons.add(this.addDrawableChild(new ButtonWidget2(
                anchorX + 15, anchorY - 10, 20, 20, new LiteralText("˃"),
                button -> this.client.options.rightKey.setPressed(true),
                button -> this.client.options.rightKey.setPressed(false)
        )));

        int mouseAnchorX = this.width / 2 - 10;
        int mouseAnchorY = this.height / 2 - 10;

        ButtonWidget2 leftMouseButton = new ButtonWidget2(
                mouseAnchorX - 20, mouseAnchorY, 20, 20, new LiteralText("L"),
                button -> this.client.options.attackKey.setPressed(true),
                button -> this.client.options.attackKey.setPressed(false)
        );
        MouseKeyPresser keyPresser = new MouseKeyPresser(client.options.attackKey, leftMouseButton);
        pressers.add(keyPresser);
        this.buttons.add(this.addDrawableChild(leftMouseButton));
        this.buttons.add(this.addDrawableChild(new ButtonWidget2(
                mouseAnchorX + 20, mouseAnchorY, 20, 20, new LiteralText("R"),
                button -> this.client.options.useKey.setPressed(true),
                button -> this.client.options.useKey.setPressed(false)
        )));

        for (ButtonWidget button : buttons) {
            button.setAlpha(0.5f);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.fillGradient(matrices, 0, 0, this.width, this.height, 0x60000000, 0x60000000);
        matrices.push();
        // matrices.scale(2.0f, 2.0f, 2.0f);
        DrawableHelper.drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        var player = this.client.player;
        String position = String.format("Pos: (%.2f, %.2f, %.2f)", player.getX(), player.getY(), player.getZ());
        DrawableHelper.drawStringWithShadow(matrices, this.textRenderer, position, 0, this.height - 20, 0x50FFFFFF);
        String dir = String.format("Dir: (%.2f, %.2f)", player.getYaw(), player.getPitch());
        DrawableHelper.drawStringWithShadow(matrices, this.textRenderer, dir, 0, this.height - 10, 0x50FFFFFF);

        matrices.pop();
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        for (MouseKeyPresser presser : pressers) {
            presser.tick();
        }
        // LOGGER.info("Test screen tick");
    }

    @Override
    public boolean shouldPause() {
        return false;
    }


    private void startScript(ButtonWidget button) {
        ScheduledExecutorService executor = myUtils2.getExecutor();
        PlayerMotion playerMotion = myUtils2.getPlayerMotion();

        playerMotion.moveForward(100);
        playerMotion.changeLookDirection(-5, 0, 100);
        playerMotion.changeLookDirection(5, 0, 100);
        playerMotion.moveForward(100);
    }

    private class MouseKeyPresser {

        private KeyBinding keyBinding;
        private KeyPressable keyPressable;
        private ButtonWidget2 button;

        public MouseKeyPresser(KeyBinding keyBinding, ButtonWidget2 button) {
            this.keyBinding = keyBinding;
            this.keyPressable = KeyPressable.of(keyBinding);
            this.button = button;
        }

        public void tick() {
            if (button.isPressed()) {
                keyPressable.onKeyPressed();
                LOGGER.info("Make Pressed!");
            }
        }
    }
}
