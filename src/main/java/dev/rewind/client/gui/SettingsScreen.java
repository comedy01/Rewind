package dev.rewind.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rewind.RewindSettings;
import dev.rewind.client.RewindClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class SettingsScreen extends InputScreen {
    private static final int WIDTH = 250;
    private static final int ROW = 24;

    private final Screen parent;
    private Button keyButton;
    private Button resetButton;
    private boolean listening;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("rewind.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - WIDTH / 2;
        int y = height / 6;

        keyButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            listening = true;
            updateKey();
        }).bounds(x, y, WIDTH - 54, 20).tooltip(Tooltip.create(Component.translatable("rewind.settings.key.tooltip"))).build());
        resetButton = addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> {
            KeyMapping key = RewindClient.key();
            if (key != null) {
                bind(key, key.getDefaultKey());
            }
        }).bounds(x + WIDTH - 50, y, 50, 20).build());
        updateKey();

        y += ROW;
        addRenderableWidget(new HistorySlider(x, y, WIDTH));

        y += ROW;
        addRenderableWidget(new JumpSlider(x, y, WIDTH));

        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(RewindSettings.deathButton())
                .withTooltip(value -> Tooltip.create(Component.translatable("rewind.settings.death_button.tooltip")))
                .create(x, y, WIDTH, 20, Component.translatable("rewind.settings.death_button"),
                        (button, value) -> RewindSettings.setDeathButton(value)));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height / 6 + 5 * ROW + 12, 200, 20).build());
    }

    @Override
    protected boolean key(KeyPress press) {
        if (listening) {
            KeyMapping key = RewindClient.key();
            if (press.escape() || key == null) {
                listening = false;
                updateKey();
            } else {
                bind(key, press.input());
            }
            return true;
        }
        return false;
    }

    private void bind(KeyMapping key, InputConstants.Key input) {
        listening = false;
        key.setKey(input);
        KeyMapping.resetMapping();
        minecraft.options.save();
        updateKey();
    }

    private void updateKey() {
        KeyMapping key = RewindClient.key();
        if (key == null) {
            keyButton.active = false;
            resetButton.active = false;
            keyButton.setMessage(Component.translatable("rewind.settings.key", "?"));
            return;
        }
        MutableComponent name = key.getTranslatedKeyMessage().copy();
        if (listening) {
            name = Component.literal("> ").append(name.withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                    .append(" <").withStyle(ChatFormatting.YELLOW);
        } else if (conflicts(key)) {
            name = name.withStyle(ChatFormatting.RED);
        }
        keyButton.setMessage(Component.translatable("rewind.settings.key", name));
        resetButton.active = !key.isDefault();
    }

    private boolean conflicts(KeyMapping key) {
        if (key.isUnbound()) {
            return false;
        }
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (other != key && other.same(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void draw(Canvas graphics, int mouseX, int mouseY) {
        graphics.centered(title, width / 2, 15, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        RewindSettings.save();
        ScreenOpener.open(minecraft, parent);
    }

    private static final class HistorySlider extends AbstractSliderButton {
        private final int[] limits = RewindSettings.availableHistoryLimits();

        HistorySlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), 0);
            int index = 0;
            int current = RewindSettings.historyLimitMb();
            for (int i = 0; i < limits.length; i++) {
                if (limits[i] <= current) {
                    index = i;
                }
            }
            value = limits.length < 2 ? 0 : index / (double) (limits.length - 1);
            setTooltip(Tooltip.create(Component.translatable("rewind.settings.history.tooltip")));
            updateMessage();
        }

        private int selected() {
            return limits[(int) Math.round(value * (limits.length - 1))];
        }

        @Override
        protected void updateMessage() {
            int mb = selected();
            Component amount = mb == 0
                    ? Component.translatable("rewind.settings.history.auto", RewindSettings.autoHistoryBudget() >> 20)
                    : Component.translatable("rewind.settings.history.mb", mb);
            setMessage(Component.translatable("rewind.settings.history", amount));
        }

        @Override
        protected void applyValue() {
            RewindSettings.setHistoryLimitMb(selected());
        }
    }

    private static final class JumpSlider extends AbstractSliderButton {
        private static final int STEPS = RewindSettings.MAX_JUMP_SECONDS - RewindSettings.MIN_JUMP_SECONDS;

        JumpSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(),
                    (RewindSettings.jumpBeforeSeconds() - RewindSettings.MIN_JUMP_SECONDS) / (double) STEPS);
            setTooltip(Tooltip.create(Component.translatable("rewind.settings.jump.tooltip")));
            updateMessage();
        }

        private int selected() {
            return RewindSettings.MIN_JUMP_SECONDS + (int) Math.round(value * STEPS);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("rewind.settings.jump", selected()));
        }

        @Override
        protected void applyValue() {
            RewindSettings.setJumpBeforeSeconds(selected());
        }
    }
}
