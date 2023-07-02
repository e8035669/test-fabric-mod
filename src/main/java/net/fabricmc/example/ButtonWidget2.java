package net.fabricmc.example;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ButtonWidget2 extends ButtonWidget {

    private final ReleaseAction onRelease;
    private boolean lastHovered;
    private boolean isPressed;


    public ButtonWidget2(int x, int y, int width, int height, Text message, PressAction onPress,
                         ReleaseAction onRelease) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
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
