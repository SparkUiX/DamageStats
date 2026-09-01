package com.u1x.damagestats.client.screen;

import com.u1x.damagestats.DamageStatsText;
import com.u1x.damagestats.trace.DamageTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DamageHistoryScreen extends Screen {
    private static final int ROW_HEIGHT = 42;

    private final List<DamageTrace> sourceTraces;
    private List<DamageTrace> visibleTraces;
    private boolean damageDescending;
    private double scrollAmount;
    private Button timeButton;
    private Button damageButton;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    public DamageHistoryScreen(List<DamageTrace> traces, boolean damageDescending) {
        super(DamageStatsText.text("screen.history.title"));
        this.sourceTraces = List.copyOf(traces);
        this.damageDescending = damageDescending;
        this.visibleTraces = sorted();
    }

    @Override
    protected void init() {
        int startX = width / 2 - 104;
        timeButton = addRenderableWidget(Button.builder(DamageStatsText.text("button.history.time"),
                        button -> setDamageDescending(false))
                .tooltip(Tooltip.create(DamageStatsText.text("tooltip.history.time")))
                .bounds(startX, 35, 100, 20).build());
        damageButton = addRenderableWidget(Button.builder(DamageStatsText.text("button.history.damage"),
                        button -> setDamageDescending(true))
                .tooltip(Tooltip.create(DamageStatsText.text("tooltip.history.damage")))
                .bounds(startX + 108, 35, 100, 20).build());
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font, DamageStatsText.string("screen.history.help"),
                width / 2, 22, 0xA8A8A8);

        int left = Math.max(12, width / 2 - 280);
        int right = Math.min(width - 12, width / 2 + 280);
        int top = 61;
        int bottom = height - 16;
        graphics.enableScissor(left, top, right, bottom);
        int y = top - (int) scrollAmount;
        ItemStack hoveredItem = ItemStack.EMPTY;
        for (DamageTrace trace : visibleTraces) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                boolean hovered = inside(mouseX, mouseY, left, right, y, y + ROW_HEIGHT - 2);
                graphics.fill(left, y, right, y + ROW_HEIGHT - 2, hovered ? 0xB0343434 : 0xA0181818);
                ItemStack weapon = trace.weapon().itemStack();
                int textX = left + 6;
                if (!weapon.isEmpty()) {
                    graphics.renderItem(weapon, left + 5, y + 5);
                    textX += 22;
                    if (inside(mouseX, mouseY, left + 5, left + 23, y + 5, y + 23)) hoveredItem = weapon;
                }
                String line = "#" + trace.sequence() + "  " + trace.weapon().displayName() + " → "
                        + trace.targetName() + DamageStatsText.string("screen.history.damage", number(trace.calculatedDamage()));
                graphics.drawString(font, shorten(line, right - textX - 6), textX, y + 6, 0xFFD35A, false);
                String detail = DamageStatsText.string("screen.history.detail", number(trace.actualHealthLoss()),
                        trace.gameTime(), trace.steps().size());
                graphics.drawString(font, detail, textX, y + 23, 0xA8A8A8, false);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        renderScrollbar(graphics, right, top, bottom);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hoveredItem.isEmpty()) graphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = Math.max(12, width / 2 - 280);
            int right = Math.min(width - 12, width / 2 + 280);
            int top = 61;
            int bottom = height - 16;
            if (mouseX >= right - 7 && mouseX < right && mouseY >= top && mouseY < bottom && maxScroll() > 0) {
                int thumbTop = thumbTop(top, bottom);
                int thumbHeight = thumbHeight(top, bottom);
                draggingScrollbar = true;
                scrollbarGrabOffset = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight
                        ? mouseY - thumbTop : thumbHeight / 2.0D;
                updateScrollFromMouse(mouseY, top, bottom);
                return true;
            }
            if (inside(mouseX, mouseY, left, right, top, bottom)) {
                int index = (int) ((mouseY - top + scrollAmount) / ROW_HEIGHT);
                if (index >= 0 && index < visibleTraces.size()) {
                    Minecraft.getInstance().setScreen(new DamageTraceScreen(visibleTraces.get(index)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollAmount = Mth.clamp(scrollAmount - delta * 24.0D, 0.0D, maxScroll());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY, 61, height - 16);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderScrollbar(GuiGraphics graphics, int right, int top, int bottom) {
        if (maxScroll() <= 0) return;
        graphics.fill(right - 6, top, right - 2, bottom, 0x70404040);
        int thumbTop = thumbTop(top, bottom);
        int thumbHeight = thumbHeight(top, bottom);
        graphics.fill(right - 7, thumbTop, right - 1, thumbTop + thumbHeight,
                draggingScrollbar ? 0xFFE0E0E0 : 0xFFC0C0C0);
    }

    private void updateScrollFromMouse(double mouseY, int top, int bottom) {
        int thumbHeight = thumbHeight(top, bottom);
        double track = Math.max(1.0D, bottom - top - thumbHeight);
        double position = Mth.clamp(mouseY - top - scrollbarGrabOffset, 0.0D, track);
        scrollAmount = position / track * maxScroll();
    }

    private int thumbTop(int top, int bottom) {
        int thumbHeight = thumbHeight(top, bottom);
        return top + (int) ((bottom - top - thumbHeight) * (scrollAmount / Math.max(1.0D, maxScroll())));
    }

    private int thumbHeight(int top, int bottom) {
        int viewport = bottom - top;
        int content = visibleTraces.size() * ROW_HEIGHT;
        return Math.max(20, (int) ((double) viewport * viewport / Math.max(viewport, content)));
    }

    private double maxScroll() {
        return Math.max(0, visibleTraces.size() * ROW_HEIGHT - Math.max(0, height - 77));
    }

    private void setDamageDescending(boolean value) {
        damageDescending = value;
        visibleTraces = sorted();
        scrollAmount = 0.0D;
        updateButtons();
    }

    private List<DamageTrace> sorted() {
        List<DamageTrace> result = new ArrayList<>(sourceTraces);
        if (damageDescending) {
            result.sort(Comparator.comparingDouble(DamageTrace::calculatedDamage).reversed()
                    .thenComparing(Comparator.comparingLong(DamageTrace::sequence).reversed()));
        } else {
            result.sort(Comparator.comparingLong(DamageTrace::sequence).reversed());
        }
        return result;
    }

    private void updateButtons() {
        if (timeButton == null) return;
        timeButton.active = damageDescending;
        damageButton.active = !damageDescending;
    }

    private String shorten(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text
                : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static boolean inside(double x, double y, int left, int right, int top, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private static String number(float value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
