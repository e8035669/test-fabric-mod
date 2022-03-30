package net.fabricmc.example;

import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class MyTestScreen extends Screen {

    private final List<ButtonWidget> buttons = Lists.newArrayList();

    protected MyTestScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.buttons.clear();
        this.buttons.add(this.addDrawableChild(
                new ButtonWidget(this.width / 2 - 100, this.height / 2 + 10, 200, 20,
                        new TranslatableText("jeff.text.test_button"), button -> {
                    this.client.player.sendMessage(new LiteralText("Button click"), false);
                })
        ));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.fillGradient(matrices, 0, 0, this.width, this.height, 0x60000000, 0x60000000);
        matrices.push();
        matrices.scale(2.0f, 2.0f, 2.0f);
        DeathScreen.drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2 / 2, 30, 0xFFFFFF);
        matrices.pop();
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
