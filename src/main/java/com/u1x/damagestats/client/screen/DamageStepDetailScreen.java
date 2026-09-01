package com.u1x.damagestats.client.screen;

import com.u1x.damagestats.DamageStatsText;
import com.u1x.damagestats.trace.DamageStep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

final class DamageStepDetailScreen extends Screen {
    private final Screen parent;
    private final List<DamageStep> steps;
    private double scroll;

    DamageStepDetailScreen(Screen parent, List<DamageStep> steps) {
        super(DamageStatsText.text("screen.detail.title"));
        this.parent = parent;
        this.steps = List.copyOf(steps);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(DamageStatsText.text("button.back"), ignored -> minecraft.setScreen(parent))
                .tooltip(Tooltip.create(DamageStatsText.text("tooltip.back")))
                .bounds(width / 2 - 50, height - 27, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        int left = Math.max(15, width / 2 - 300);
        List<FormattedCharSequence> lines = wrappedLines(Math.max(40, width - left * 2));
        int top = 27;
        int bottom = height - 34;
        graphics.enableScissor(left, top, width - left, bottom);
        int y = top - (int) scroll;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, left, y, 0xD8D8D8, false);
            y += 12;
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private List<Component> lines() {
        List<Component> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            DamageStep step = steps.get(i);
            result.add(DamageStatsText.text("screen.detail.entry", i + 1, step.before(), step.after()));
            result.add(DamageStatsText.text("screen.detail.mod_kind", step.source().modId(), step.source().kind()));
            result.add(DamageStatsText.text("screen.detail.source_id", step.source().id()));
            result.add(DamageStatsText.text("screen.detail.display_name", step.source().displayName()));
            result.add(DamageStatsText.text("screen.detail.note",
                    step.note() == null || step.note().isBlank() ? DamageStatsText.string("label.none") : step.note()));
            result.add(Component.literal(" "));
        }
        return result;
    }

    private List<FormattedCharSequence> wrappedLines(int maxWidth) {
        List<FormattedCharSequence> result = new ArrayList<>();
        for (Component line : lines()) result.addAll(font.split(line, maxWidth));
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - delta * 24.0D, 0.0D,
                Math.max(0, wrappedLines(Math.max(40, width - Math.max(15, width / 2 - 300) * 2)).size() * 12
                        - Math.max(0, height - 61)));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
