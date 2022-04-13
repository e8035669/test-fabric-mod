package net.fabricmc.example;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class ButtonWidget2 extends ButtonWidget {

    private ReleaseAction onRelease;
    private boolean lastHovered;
    private boolean isPressed;


    public ButtonWidget2(int x, int y, int width, int height, Text message, PressAction onPress,
                         ReleaseAction onRelease) {
        super(x, y, width, height, message, onPress);
        this.onRelease = onRelease;
        lastHovered = false;
        isPressed = false;
    }

    @Override
    public void onPress() {
        super.onPress();
        isPressed = true;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        onRelease.onRelease(this);
        isPressed = false;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (lastHovered != this.hovered) {
            lastHovered = this.hovered;
            if (!this.hovered && isPressed) {
                onRelease(mouseX, mouseY);
            }
        }
    }

    public boolean isPressed() {
        return isPressed;
    }

    public interface ReleaseAction {
        void onRelease(ButtonWidget button);
    }
}
