package net.fabricmc.example;

public interface MouseInterface {

    void onCursorPos(long window, double x, double y);

    void onMouseButton(long window, int button, int action, int mods);

    void onMouseScroll(long window, double horizontal, double vertical);

}
