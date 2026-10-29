package dev.rewind.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

public record KeyPress(int key, boolean shift, InputConstants.Key input) {
    public boolean escape() {
        return key == InputConstants.KEY_ESCAPE;
    }
}
