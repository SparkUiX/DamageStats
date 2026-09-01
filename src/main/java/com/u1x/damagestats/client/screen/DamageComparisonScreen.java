package com.u1x.damagestats.client.screen;

import com.u1x.damagestats.DamageStatsText;
import com.u1x.damagestats.client.ClientAnnotationStore;
import com.u1x.damagestats.trace.DamageStage;
import com.u1x.damagestats.trace.DamageStep;
import com.u1x.damagestats.trace.DamageTrace;
import com.u1x.damagestats.trace.DamageContributionCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;

public final class DamageComparisonScreen extends Screen {
    private static final int ROW_HEIGHT = 70;

    private final DamageTrace first;
    private final DamageTrace second;
    private final List<ComparisonRow> rows;
    private double scrollAmount;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    public DamageComparisonScreen(DamageTrace first, DamageTrace second) {
        super(DamageStatsText.text("screen.comparison.title"));
        this.first = first;
        this.second = second;
        this.rows = compare(first.steps(), second.steps());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        String totals = "#" + first.sequence() + "  " + number(first.calculatedDamage()) + "  ↔  #"
                + second.sequence() + "  " + number(second.calculatedDamage())
                + DamageStatsText.string("screen.comparison.total_difference",
                        signed(second.calculatedDamage() - first.calculatedDamage()));
        graphics.drawCenteredString(font, totals, width / 2, 21, 0xFFD35A);
        String actual = DamageStatsText.string("screen.comparison.actual", number(first.actualHealthLoss()),
                number(second.actualHealthLoss()), signed(second.actualHealthLoss() - first.actualHealthLoss()));
        graphics.drawCenteredString(font, actual, width / 2, 34, 0xA8E6FF);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, DamageStatsText.string("screen.comparison.no_difference"),
                    width / 2, 62, 0xA8A8A8);
        }

        int left = Math.max(12, width / 2 - 310);
        int right = Math.min(width - 12, width / 2 + 310);
        int top = 51;
        int bottom = height - 16;
        graphics.enableScissor(left, top, right, bottom);
        int y = top - (int) scrollAmount;
        ItemStack hoveredItem = ItemStack.EMPTY;
        for (ComparisonRow row : rows) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                graphics.fill(left, y, right, y + ROW_HEIGHT - 2, 0xA0181818);
                DamageStep representative = row.representative();
                int color = row.status == RowStatus.ADDED ? 0x7FE07F
                        : row.status == RowStatus.MISSING ? 0xFF7777 : 0xFFD35A;
                String status = switch (row.status) {
                    case ADDED -> DamageStatsText.string("comparison.added");
                    case MISSING -> DamageStatsText.string("comparison.missing");
                    case OPERATION_CHANGED -> DamageStatsText.string("comparison.operation_changed");
                    case CONTEXT_CHANGED -> DamageStatsText.string("comparison.context_changed");
                };
                ItemStack manualIcon = ClientAnnotationStore.icon(representative);
                String manual = manualIcon.isEmpty() ? "" : DamageStatsText.string("label.manual_annotation") + " ";
                graphics.drawString(font, status + " " + stageName(representative.stage()) + "  ·  "
                        + manual + representative.source().displayName() + "  (" + representative.source().modId() + ")",
                        left + 5, y + 5, color, false);

                String firstValue = row.first.isEmpty() ? "--" : values(row.first);
                String secondValue = row.second.isEmpty() ? "--" : values(row.second);
                graphics.drawString(font, "#" + first.sequence() + ": " + firstValue,
                        left + 5, y + 20, 0xC0C0C0, false);
                graphics.drawString(font, "#" + second.sequence() + ": " + secondValue,
                        left + 5, y + 34, 0xC0C0C0, false);

                if (!row.first.isEmpty() && !row.second.isEmpty()) {
                    float contributionDifference = contribution(row.second) - contribution(row.first);
                    graphics.drawString(font, DamageStatsText.string("screen.comparison.contribution_difference",
                                    signed(contributionDifference)),
                            Math.max(left + 5, right - 165), y + 48,
                            Math.abs(contributionDifference) < 0.0001F ? 0x888888 : color, false);
                }

                int iconX = right - 42;
                ItemStack firstItem = row.first.isEmpty() ? ItemStack.EMPTY : row.first.get(0).source().itemStack();
                ItemStack secondItem = row.second.isEmpty() ? ItemStack.EMPTY : row.second.get(0).source().itemStack();
                if (!manualIcon.isEmpty()) {
                    firstItem = manualIcon;
                    secondItem = ItemStack.EMPTY;
                }
                if (!firstItem.isEmpty()) {
                    graphics.renderItem(firstItem, iconX, y + 5);
                    if (inside(mouseX, mouseY, iconX, iconX + 18, y + 5, y + 23)) hoveredItem = firstItem;
                }
                if (!secondItem.isEmpty()) {
                    graphics.renderItem(secondItem, iconX + 20, y + 5);
                    if (inside(mouseX, mouseY, iconX + 20, iconX + 38, y + 5, y + 23)) hoveredItem = secondItem;
                }

                if (representative.note() != null && !representative.note().isBlank()) {
                    graphics.drawString(font, shorten(representative.note(), right - left - 10),
                            left + 5, y + 58, 0x888888, false);
                }
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        renderScrollbar(graphics, right, top, bottom);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hoveredItem.isEmpty()) graphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollAmount = Mth.clamp(scrollAmount - delta * 24.0D, 0.0D, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int right = Math.min(width - 12, width / 2 + 310);
        int top = 51;
        int bottom = height - 16;
        if (button == 0 && mouseX >= right - 7 && mouseX < right
                && mouseY >= top && mouseY < bottom && maxScroll() > 0) {
            int thumbTop = thumbTop(top, bottom);
            int thumbHeight = thumbHeight(top, bottom);
            draggingScrollbar = true;
            scrollbarGrabOffset = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight
                    ? mouseY - thumbTop : thumbHeight / 2.0D;
            updateScrollFromMouse(mouseY, top, bottom);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY, 51, height - 16);
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
        int content = rows.size() * ROW_HEIGHT;
        return Math.max(20, (int) ((double) viewport * viewport / Math.max(viewport, content)));
    }

    private double maxScroll() {
        return Math.max(0, rows.size() * ROW_HEIGHT - Math.max(0, height - 67));
    }

    private String shorten(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text
                : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    static List<ComparisonRow> compare(List<DamageStep> first, List<DamageStep> second) {
        Map<StepKey, List<DamageStep>> firstGroups = grouped(first);
        Map<StepKey, List<DamageStep>> secondGroups = grouped(second);
        Map<StepKey, Boolean> keys = new LinkedHashMap<>();
        firstGroups.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
        secondGroups.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
        List<ComparisonRow> result = new ArrayList<>();
        for (StepKey key : keys.keySet()) {
            List<DamageStep> left = firstGroups.getOrDefault(key, List.of());
            List<DamageStep> right = secondGroups.getOrDefault(key, List.of());
            RowStatus status = status(left, right);
            if (status != null) result.add(new ComparisonRow(left, right, status));
        }
        result.sort(Comparator.comparingInt((ComparisonRow row) -> row.status.priority)
                .thenComparing(Comparator.comparingDouble(DamageComparisonScreen::differenceMagnitude).reversed()));
        return List.copyOf(result);
    }

    private static Map<StepKey, List<DamageStep>> grouped(List<DamageStep> steps) {
        Map<StepKey, List<DamageStep>> result = new LinkedHashMap<>();
        for (DamageStep step : steps) result.computeIfAbsent(StepKey.of(step), ignored -> new ArrayList<>()).add(step);
        return result;
    }

    private static RowStatus status(List<DamageStep> first, List<DamageStep> second) {
        if (first.isEmpty()) return RowStatus.ADDED;
        if (second.isEmpty()) return RowStatus.MISSING;
        if (!operationSequence(first).equals(operationSequence(second))) return RowStatus.OPERATION_CHANGED;
        if (Math.abs(first.get(0).before() - second.get(0).before()) > 0.0001F
                || Math.abs(last(first).after() - last(second).after()) > 0.0001F) return RowStatus.CONTEXT_CHANGED;
        return null;
    }

    private static float differenceMagnitude(ComparisonRow row) {
        float firstContribution = contribution(row.first);
        float secondContribution = contribution(row.second);
        return Math.abs(secondContribution - firstContribution);
    }

    private static float contribution(List<DamageStep> steps) {
        float total = 0.0F;
        for (DamageStep step : steps) total += step.after() - step.before();
        return total;
    }

    private static String values(List<DamageStep> steps) {
        return DamageStatsText.string("screen.comparison.values", operationSequence(steps),
                number(steps.get(0).before()), number(last(steps).after()), steps.size());
    }

    private static String operationSequence(List<DamageStep> steps) {
        List<String> operations = new ArrayList<>(steps.size());
        for (DamageStep step : steps) {
            String signature = DamageContributionCalculator.operationSignature(step);
            if (signature.startsWith("multiply:")) operations.add("×" + number(step.before() == 0 ? 0 : step.after() / step.before()));
            else if (signature.startsWith("add:")) operations.add("Δ" + signed(step.after() - step.before()));
            else operations.add("→" + number(step.after()));
        }
        return String.join(" ", operations);
    }

    private static DamageStep last(List<DamageStep> steps) {
        return steps.get(steps.size() - 1);
    }

    private static boolean inside(double x, double y, int left, int right, int top, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private static String number(float value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String signed(float value) {
        return (value >= 0 ? "+" : "") + number(value);
    }

    private static String stageName(DamageStage stage) {
        return DamageStatsText.stage(stage).getString();
    }

    record ComparisonRow(List<DamageStep> first, List<DamageStep> second, RowStatus status) {
        DamageStep representative() {
            return second.isEmpty() ? first.get(0) : second.get(0);
        }
    }

    private enum RowStatus {
        MISSING(0), ADDED(0), OPERATION_CHANGED(1), CONTEXT_CHANGED(2);
        private final int priority;
        RowStatus(int priority) { this.priority = priority; }
    }

    private record StepKey(DamageStage stage, String modId, String kind, String id) {
        static StepKey of(DamageStep step) {
            return new StepKey(step.stage(), step.source().modId(), step.source().kind(),
                    step.source().id());
        }
    }
}
