package com.u1x.damagestats.client.screen;

import com.u1x.damagestats.DamageStatsText;
import com.u1x.damagestats.client.ClientAnnotationStore;
import com.u1x.damagestats.client.ClientItemCandidates;
import com.u1x.damagestats.trace.DamageStep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class ItemAnnotationScreen extends Screen {
    private static final int CELL = 24;
    private final Screen parent;
    private final DamageStep step;
    private final List<ItemStack> items = ClientItemCandidates.collect();
    private double scroll;

    ItemAnnotationScreen(Screen parent, DamageStep step) {
        super(DamageStatsText.text("screen.annotation.title"));
        this.parent = parent;
        this.step = step;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(DamageStatsText.text("button.annotation.clear"), ignored -> {
            ClientAnnotationStore.clear(step);
            minecraft.setScreen(parent);
        }).tooltip(Tooltip.create(DamageStatsText.text("tooltip.annotation.clear")))
                .bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(DamageStatsText.text("button.back"), ignored -> minecraft.setScreen(parent))
                .tooltip(Tooltip.create(DamageStatsText.text("tooltip.back")))
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font, DamageStatsText.string("screen.annotation.help"),
                width / 2, 22, 0xA8A8A8);
        graphics.drawCenteredString(font, shorten(DamageStatsText.string("screen.annotation.scope",
                        ClientAnnotationStore.classification(step)), Math.max(40, width - 24)),
                width / 2, 34, 0xFFD35A);
        int left = gridLeft();
        int top = 52;
        int right = gridRight();
        int bottom = height - 36;
        int columns = columns();
        graphics.enableScissor(left, top, right, bottom);
        ItemStack hovered = ItemStack.EMPTY;
        for (int index = 0; index < items.size(); index++) {
            int x = left + (index % columns) * CELL;
            int y = top + (index / columns) * CELL - (int) scroll;
            if (y + CELL < top || y >= bottom) continue;
            boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            graphics.fill(x, y, x + CELL - 2, y + CELL - 2, over ? 0xC0606060 : 0xA0303030);
            graphics.renderItem(items.get(index), x + 3, y + 3);
            if (over) hovered = items.get(index);
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseX < gridLeft() || mouseX >= gridRight() || mouseY < 52 || mouseY >= height - 36) {
            return false;
        }
        int column = (int) (mouseX - gridLeft()) / CELL;
        int row = (int) (mouseY - 52 + scroll) / CELL;
        int index = row * columns() + column;
        if (index >= 0 && index < items.size()) {
            ClientAnnotationStore.set(step, items.get(index));
            minecraft.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - delta * CELL, 0.0D, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int columns() { return Math.max(1, (gridRight() - gridLeft()) / CELL); }
    private int gridLeft() { return Math.max(12, width / 2 - 240); }
    private int gridRight() { return Math.min(width - 12, width / 2 + 240); }
    private String shorten(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text
                : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
    private double maxScroll() {
        int rows = (items.size() + columns() - 1) / columns();
        return Math.max(0, rows * CELL - Math.max(0, height - 88));
    }
}
