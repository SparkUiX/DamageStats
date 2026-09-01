package com.u1x.damagestats.client.screen;

import com.u1x.damagestats.DamageStatsText;
import com.u1x.damagestats.client.ClientAnnotationStore;
import com.u1x.damagestats.trace.AttributionConfidence;
import com.u1x.damagestats.trace.ContributionSource;
import com.u1x.damagestats.trace.DamageContributionCalculator;
import com.u1x.damagestats.trace.DamageStage;
import com.u1x.damagestats.trace.DamageStep;
import com.u1x.damagestats.trace.DamageTrace;
import com.u1x.damagestats.trace.EffectPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DamageTraceScreen extends Screen {
    private static final int COMPACT_HEIGHT = 35;
    private static final int DETAIL_HEIGHT = 27;
    private static final int SECTION_HEIGHT = 20;
    private static final ResourceLocation GUI_ICONS =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/icons.png");

    private final DamageTrace trace;
    private final List<GroupKey> expandedGroups = new ArrayList<>();
    private SortMode sortMode = SortMode.ACTUAL_ORDER;
    private List<StepGroup> groups = List.of();
    private double scrollAmount;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private Button actualButton;
    private Button instantButton;
    private Button removalButton;
    private Button modButton;
    private Button splitButton;
    private boolean splitIncreaseDecrease;
    private StepGroup contextGroup;
    private int contextX;
    private int contextY;
    private EffectPreview hoveredEffect;

    public DamageTraceScreen(DamageTrace trace) {
        super(DamageStatsText.text("screen.trace.title"));
        this.trace = trace;
        rebuildGroups();
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(90, Math.max(54, (width - 34) / 5));
        int totalWidth = buttonWidth * 5 + 16;
        int startX = (width - totalWidth) / 2;
        actualButton = addRenderableWidget(button("button.sort.actual", "tooltip.sort.actual",
                startX, buttonWidth, SortMode.ACTUAL_ORDER));
        instantButton = addRenderableWidget(button("button.sort.instant", "tooltip.sort.instant",
                startX + buttonWidth + 4, buttonWidth, SortMode.INSTANT_DELTA));
        removalButton = addRenderableWidget(button("button.sort.removal", "tooltip.sort.removal",
                startX + (buttonWidth + 4) * 2, buttonWidth, SortMode.REMOVAL_LOSS));
        modButton = addRenderableWidget(button("button.sort.mod", "tooltip.sort.mod",
                startX + (buttonWidth + 4) * 3, buttonWidth, SortMode.MOD_GROUP));
        splitButton = addRenderableWidget(Button.builder(DamageStatsText.text("button.split"), ignored -> toggleSplit())
                .tooltip(Tooltip.create(DamageStatsText.text("tooltip.split")))
                .bounds(startX + (buttonWidth + 4) * 4, 40, buttonWidth, 20).build());
        updateButtons();
    }

    private Button button(String labelKey, String tooltipKey, int x, int width, SortMode mode) {
        return Button.builder(DamageStatsText.text(labelKey), ignored -> setSortMode(mode))
                .tooltip(Tooltip.create(DamageStatsText.text(tooltipKey))).bounds(x, 40, width, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font,
                "#" + trace.sequence() + "  " + trace.weapon().displayName() + " → " + trace.targetName()
                        + DamageStatsText.string("screen.trace.summary", number(trace.calculatedDamage()),
                                number(trace.actualHealthLoss())),
                width / 2, 23, 0xFFD35A);

        int left = contentLeft();
        int right = contentRight();
        int top = contentTop();
        int bottom = contentBottom();
        clampScroll();
        graphics.enableScissor(left, top, right, bottom);
        int y = top - (int) scrollAmount;
        ItemStack hoveredItem = ItemStack.EMPTY;
        hoveredEffect = null;

        for (StepGroup group : groups) {
            int rowHeight = rowHeight(group);
            if (y + rowHeight >= top && y <= bottom) {
                if (group.section) {
                    graphics.fill(left, y, right - 9, y + SECTION_HEIGHT - 2, 0xB0202020);
                    graphics.drawString(font, group.sectionTitle, left + 7, y + 6, group.sectionColor, false);
                    y += rowHeight;
                    continue;
                }
                boolean hovered = inside(mouseX, mouseY, left, right - 9, y, y + rowHeight - 2);
                graphics.fill(left, y, right - 9, y + rowHeight - 2, hovered ? 0xB0343434 : 0xA0181818);
                ItemStack candidate = renderGroup(graphics, group, left, right - 9, y, mouseX, mouseY);
                if (!candidate.isEmpty()) hoveredItem = candidate;
            }
            y += rowHeight;
        }
        graphics.disableScissor();
        renderScrollbar(graphics, right, top, bottom);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hoveredItem.isEmpty()) graphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        else if (hoveredEffect != null) renderEffectTooltip(graphics, hoveredEffect, mouseX, mouseY);
        renderContextMenu(graphics, mouseX, mouseY);
    }

    private ItemStack renderGroup(GuiGraphics graphics, StepGroup group, int left, int right, int y,
            int mouseX, int mouseY) {
        IndexedStep representative = group.members.get(0);
        DamageStep step = representative.step;
        int color = confidenceColor(step.confidence());
        String count = group.members.size() > 1 ? group.members.size() + "× " : "";
        String score = switch (sortMode) {
            case INSTANT_DELTA -> DamageStatsText.string("screen.trace.score.instant",
                    number(DamageContributionCalculator.instantMagnitude(step)));
            case REMOVAL_LOSS -> DamageStatsText.string("screen.trace.score.removal", signed(removalLoss(group)));
            default -> "";
        };
        String index = group.members.size() > 1
                ? "#" + (group.members.get(0).originalIndex + 1) + "… " : "#" + (representative.originalIndex + 1) + " ";
        String first = index + count + stageName(step.stage()) + "  " + compactTransformation(group)
                + score + "  [" + confidenceName(step.confidence()) + "]";
        graphics.drawString(font, shorten(first, right - left - 10), left + 5, y + 5, color, false);

        ContributionSource source = step.source();
        ItemStack annotation = ClientAnnotationStore.icon(step);
        ItemStack item = annotation.isEmpty() ? source.itemStack() : annotation;
        int textX = left + 5;
        ItemStack hoveredItem = ItemStack.EMPTY;
        if (!item.isEmpty()) {
            graphics.renderItem(item, left + 5, y + 17);
            textX += 22;
            if (inside(mouseX, mouseY, left + 5, left + 23, y + 17, y + 35)) hoveredItem = item;
            ItemStack secondary = source.secondaryItemStack();
            if (annotation.isEmpty() && !secondary.isEmpty()) {
                graphics.renderItem(secondary, left + 25, y + 17);
                textX += 20;
                if (inside(mouseX, mouseY, left + 25, left + 43, y + 17, y + 35)) hoveredItem = secondary;
            }
        } else if (source.effectPreview() != null) {
            renderEffectIcon(graphics, source.effectPreview(), left + 5, y + 17);
            textX += 22;
            if (inside(mouseX, mouseY, left + 5, left + 23, y + 17, y + 35)) hoveredEffect = source.effectPreview();
        } else if (step.stage() == DamageStage.ARMOR) {
            graphics.blit(GUI_ICONS, left + 5, y + 20, 34, 9, 9, 9);
            textX += 14;
        }
        String collapsedMarker = isExpanded(group.key) ? "▼ " : "▶ ";
        String manual = annotation.isEmpty() ? "" : DamageStatsText.string("label.manual_annotation") + " ";
        String sourceLine = collapsedMarker + manual + DamageStatsText.resolve(source.displayName())
                + "  ·  " + modName(source.modId());
        graphics.drawString(font, shorten(sourceLine, right - textX - 5), textX, y + 20, 0xE0E0E0, false);

        if (isExpanded(group.key)) {
            int detailY = y + COMPACT_HEIGHT;
            for (IndexedStep member : group.members) {
                DamageStep detail = member.step;
                graphics.fill(left + 4, detailY, right - 4, detailY + DETAIL_HEIGHT - 1, 0x70242424);
                String classLine = "#" + (member.originalIndex + 1) + "  "
                        + DamageStatsText.string("label.source_class", classHint(detail));
                graphics.drawString(font, shorten(classLine, right - left - 14), left + 7, detailY + 3,
                        detail.confidence() == AttributionConfidence.EXACT ? 0xA8A8A8 : 0xFFD35A, false);
                String note = detail.note() == null || detail.note().isBlank()
                        ? DamageStatsText.string("label.no_note") : DamageStatsText.resolve(detail.note());
                graphics.drawString(font, shorten(DamageStatsText.string("label.note", note), right - left - 14),
                        left + 7, detailY + 15, 0x8F8F8F, false);
                detailY += DETAIL_HEIGHT;
            }
        }
        return hoveredItem;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextGroup != null && button == 0) {
            int option = contextOption(mouseX, mouseY);
            StepGroup selected = contextGroup;
            contextGroup = null;
            if (option == 0) {
                minecraft.setScreen(new DamageStepDetailScreen(this,
                        selected.members.stream().map(IndexedStep::step).toList()));
                return true;
            }
            if (option == 1) {
                minecraft.setScreen(new ItemAnnotationScreen(this, selected.members.get(0).step));
                return true;
            }
            if (option == 2) {
                ClientAnnotationStore.clear(selected.members.get(0).step);
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 && button != 1) return false;
        int right = contentRight();
        int top = contentTop();
        int bottom = contentBottom();
        if (mouseX >= right - 9 && mouseX < right && mouseY >= top && mouseY < bottom && maxScroll() > 0) {
            int thumbTop = scrollbarThumbTop(top, bottom);
            int thumbHeight = scrollbarThumbHeight(top, bottom);
            draggingScrollbar = true;
            scrollbarGrabOffset = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight
                    ? mouseY - thumbTop : thumbHeight / 2.0D;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (!inside(mouseX, mouseY, contentLeft(), right - 9, top, bottom)) return false;
        double contentY = mouseY - top + scrollAmount;
        int cursor = 0;
        for (StepGroup group : groups) {
            int rowHeight = rowHeight(group);
            if (contentY >= cursor && contentY < cursor + rowHeight) {
                if (group.section) return false;
                if (button == 1) {
                    contextGroup = group;
                    contextX = Mth.clamp((int) mouseX, 4, Math.max(4, width - 132));
                    int rowTop = top + cursor - (int) scrollAmount;
                    contextY = Mth.clamp(rowTop, 4, Math.max(4, this.height - 64));
                    return true;
                }
                if (isExpanded(group.key)) expandedGroups.remove(group.key);
                else expandedGroups.add(group.key);
                clampScroll();
                return true;
            }
            cursor += rowHeight;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollAmount = Mth.clamp(scrollAmount - delta * 24.0D, 0.0D, maxScroll());
        return true;
    }

    private void setSortMode(SortMode mode) {
        contextGroup = null;
        sortMode = mode;
        expandedGroups.clear();
        scrollAmount = 0.0D;
        rebuildGroups();
        updateButtons();
    }

    private void toggleSplit() {
        contextGroup = null;
        splitIncreaseDecrease = !splitIncreaseDecrease;
        expandedGroups.clear();
        scrollAmount = 0.0D;
        rebuildGroups();
        updateButtons();
    }

    private void rebuildGroups() {
        List<IndexedStep> sorted = new ArrayList<>(trace.steps().size());
        for (int i = 0; i < trace.steps().size(); i++) sorted.add(new IndexedStep(i, trace.steps().get(i)));
        switch (sortMode) {
            case INSTANT_DELTA -> sorted.sort(Comparator
                    .comparingDouble((IndexedStep value) -> DamageContributionCalculator.instantMagnitude(value.step))
                    .reversed().thenComparingInt(IndexedStep::originalIndex));
            case REMOVAL_LOSS -> sorted.sort(Comparator
                    .comparingDouble((IndexedStep value) -> DamageContributionCalculator.removalLoss(trace, value.originalIndex))
                    .reversed().thenComparingInt(IndexedStep::originalIndex));
            case MOD_GROUP -> sorted.sort(Comparator
                    .comparing((IndexedStep value) -> modName(value.step.source().modId()))
                    .thenComparing(value -> DamageStatsText.resolve(value.step.source().displayName()))
                    .thenComparingInt(IndexedStep::originalIndex));
            case ACTUAL_ORDER -> { }
        }
        List<StepGroup> base = sortMode == SortMode.ACTUAL_ORDER ? groupConsecutive(sorted) : groupGlobally(sorted);
        groups = splitIncreaseDecrease ? splitGroups(base) : base;
    }

    private List<StepGroup> splitGroups(List<StepGroup> base) {
        List<StepGroup> increases = new ArrayList<>();
        List<StepGroup> reductions = new ArrayList<>();
        List<StepGroup> neutral = new ArrayList<>();
        for (StepGroup group : base) {
            float change = group.members.stream().map(IndexedStep::step)
                    .map(DamageTraceScreen::directionalChange).reduce(0.0F, Float::sum);
            if (change > 0.0001F) increases.add(group);
            else if (change < -0.0001F) reductions.add(group);
            else neutral.add(group);
        }
        List<StepGroup> result = new ArrayList<>(base.size() + 3);
        appendSection(result, DamageStatsText.string("section.increase"), 0x7FE07F, increases);
        appendSection(result, DamageStatsText.string("section.reduction"), 0xFF8A8A, reductions);
        appendSection(result, DamageStatsText.string("section.neutral"), 0xB8B8B8, neutral);
        return List.copyOf(result);
    }

    private static float directionalChange(DamageStep step) {
        return switch (step.stage()) {
            case LIVING_ATTACK, ACTUAL_HEALTH_LOSS, PIPELINE_TERMINATED -> 0.0F;
            default -> step.after() - step.before();
        };
    }

    private static void appendSection(List<StepGroup> result, String title, int color, List<StepGroup> members) {
        if (members.isEmpty()) return;
        result.add(StepGroup.section(title, color));
        result.addAll(members);
    }

    private List<StepGroup> groupConsecutive(List<IndexedStep> steps) {
        List<StepGroup> result = new ArrayList<>();
        for (IndexedStep indexed : steps) {
            GroupKey key = key(indexed);
            StepGroup last = result.isEmpty() ? null : result.get(result.size() - 1);
            if (last != null && last.key.equals(key) && key.groupable) last.members.add(indexed);
            else result.add(new StepGroup(key, new ArrayList<>(List.of(indexed))));
        }
        return List.copyOf(result);
    }

    private List<StepGroup> groupGlobally(List<IndexedStep> steps) {
        Map<GroupKey, StepGroup> grouped = new LinkedHashMap<>();
        List<StepGroup> result = new ArrayList<>();
        for (IndexedStep indexed : steps) {
            GroupKey key = key(indexed);
            if (!key.groupable) {
                result.add(new StepGroup(key, new ArrayList<>(List.of(indexed))));
                continue;
            }
            StepGroup group = grouped.get(key);
            if (group == null) {
                group = new StepGroup(key, new ArrayList<>());
                grouped.put(key, group);
                result.add(group);
            }
            group.members.add(indexed);
        }
        return List.copyOf(result);
    }

    private GroupKey key(IndexedStep indexed) {
        DamageStep step = indexed.step;
        boolean groupable = step.confidence() == AttributionConfidence.UNKNOWN;
        String unique = groupable ? "" : Integer.toString(indexed.originalIndex);
        return new GroupKey(step.stage(), step.source().modId(), step.source().kind(), step.source().id(),
                step.source().displayName(), step.confidence(), DamageContributionCalculator.operationSignature(step),
                groupable ? step.note() : "", groupable, unique);
    }

    private float removalLoss(StepGroup group) {
        return DamageContributionCalculator.removalLoss(trace,
                group.members.stream().map(IndexedStep::originalIndex).toList());
    }

    private String compactTransformation(StepGroup group) {
        DamageStep step = group.members.get(0).step;
        if (group.members.size() == 1) return transformation(step);
        float delta = step.after() - step.before();
        if (DamageContributionCalculator.operationSignature(step).startsWith("add:")) {
            return DamageStatsText.string("operation.each_add", signed(delta));
        }
        if (Math.abs(step.before()) > 0.0001F) {
            return DamageStatsText.string("operation.each_multiply", number(step.after() / step.before()));
        }
        return DamageStatsText.string("operation.each_set", number(step.after()));
    }

    private void renderScrollbar(GuiGraphics graphics, int right, int top, int bottom) {
        if (maxScroll() <= 0) return;
        graphics.fill(right - 7, top, right - 2, bottom, 0x70404040);
        int thumbTop = scrollbarThumbTop(top, bottom);
        int thumbHeight = scrollbarThumbHeight(top, bottom);
        graphics.fill(right - 8, thumbTop, right - 1, thumbTop + thumbHeight,
                draggingScrollbar ? 0xFFE0E0E0 : 0xFFC0C0C0);
    }

    private void updateScrollFromMouse(double mouseY) {
        int top = contentTop();
        int bottom = contentBottom();
        int thumbHeight = scrollbarThumbHeight(top, bottom);
        double track = Math.max(1.0D, bottom - top - thumbHeight);
        double position = Mth.clamp(mouseY - top - scrollbarGrabOffset, 0.0D, track);
        scrollAmount = position / track * maxScroll();
    }

    private int scrollbarThumbTop(int top, int bottom) {
        int thumbHeight = scrollbarThumbHeight(top, bottom);
        return top + (int) ((bottom - top - thumbHeight) * (scrollAmount / Math.max(1.0D, maxScroll())));
    }

    private int scrollbarThumbHeight(int top, int bottom) {
        int viewport = bottom - top;
        return Math.max(20, (int) ((double) viewport * viewport / Math.max(viewport, contentHeight())));
    }

    private int rowHeight(StepGroup group) {
        if (group.section) return SECTION_HEIGHT;
        return COMPACT_HEIGHT + (isExpanded(group.key) ? group.members.size() * DETAIL_HEIGHT : 0);
    }

    private int contentHeight() {
        int height = 0;
        for (StepGroup group : groups) height += rowHeight(group);
        return height;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - (contentBottom() - contentTop()));
    }

    private void clampScroll() {
        scrollAmount = Mth.clamp(scrollAmount, 0.0D, maxScroll());
    }

    private boolean isExpanded(GroupKey key) {
        return expandedGroups.contains(key);
    }

    private void updateButtons() {
        if (actualButton == null) return;
        actualButton.active = sortMode != SortMode.ACTUAL_ORDER;
        instantButton.active = sortMode != SortMode.INSTANT_DELTA;
        removalButton.active = sortMode != SortMode.REMOVAL_LOSS;
        modButton.active = sortMode != SortMode.MOD_GROUP;
        splitButton.setMessage(DamageStatsText.text(splitIncreaseDecrease ? "button.merge" : "button.split"));
    }

    private int contentLeft() { return Math.max(12, width / 2 - 310); }
    private int contentRight() { return Math.min(width - 12, width / 2 + 310); }
    private int contentTop() { return 66; }
    private int contentBottom() { return height - 18; }

    private String shorten(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text
                : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String transformation(DamageStep step) {
        float before = step.before();
        float after = step.after();
        float delta = after - before;
        if (Math.abs(before) < 0.0001F) return number(after);
        return DamageStatsText.string("operation.transformation", number(before), number(after),
                number(after / before), signed(delta));
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) return Float.toString(value);
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String signed(float value) {
        return (value >= 0.0F ? "+" : "") + number(value);
    }

    private static int confidenceColor(AttributionConfidence confidence) {
        return switch (confidence) {
            case EXACT -> 0x7FE07F;
            case INFERRED -> 0xFFD35A;
            case UNKNOWN -> 0xFF7777;
        };
    }

    private static String modName(String modId) {
        if ("minecraft".equals(modId)) return "Minecraft";
        if ("forge".equals(modId)) return "Minecraft Forge";
        if ("unknown".equals(modId)) return DamageStatsText.string("label.unknown_mod");
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName()).orElse(modId);
    }

    private static String classHint(DamageStep step) {
        ItemStack item = step.source().itemStack();
        if (!item.isEmpty()) return DamageStatsText.string("hint.item_class", item.getItem().getClass().getName());
        if ("attributeslib".equals(step.source().modId())) {
            return step.stage() == DamageStage.VANILLA_OR_FORGE_CRITICAL
                    ? "dev.shadowsoffire.attributeslib.impl.AttributeEvents#vanillaCritDmg"
                    : DamageStatsText.string("hint.algorithm_inference",
                            "dev.shadowsoffire.attributeslib.impl.AttributeEvents#apothCriticalStrike");
        }
        if (step.source().kind().equals("event_listener")) return step.source().id();
        return switch (step.stage()) {
            case ATTACK_ATTRIBUTE -> "net.minecraft.world.entity.ai.attributes.AttributeInstance";
            case ATTACK_COOLDOWN, VANILLA_OR_FORGE_CRITICAL, ENCHANTMENT_BONUS,
                    ATTACK_CONSTRUCTION_RESIDUAL -> "net.minecraft.world.entity.player.Player#attack";
            case LIVING_ATTACK -> "net.minecraftforge.event.entity.living.LivingAttackEvent";
            case LIVING_HURT_EVENT -> step.source().kind().equals("event_priority")
                    ? DamageStatsText.string("hint.see_candidates")
                    : "net.minecraftforge.event.entity.living.LivingHurtEvent";
            case ARMOR -> "net.minecraft.world.entity.LivingEntity#getDamageAfterArmorAbsorb";
            case MAGIC -> "net.minecraft.world.entity.LivingEntity#getDamageAfterMagicAbsorb";
            case ABSORPTION -> "net.minecraft.world.entity.LivingEntity#actuallyHurt";
            case LIVING_DAMAGE_EVENT -> step.source().kind().equals("event_priority")
                    ? DamageStatsText.string("hint.see_candidates")
                    : "net.minecraftforge.event.entity.living.LivingDamageEvent";
            case ACTUAL_HEALTH_LOSS, CANCELED_OR_BLOCKED -> "net.minecraft.world.entity.LivingEntity#actuallyHurt";
            case PIPELINE_TERMINATED -> DamageStatsText.string("hint.custom_pipeline");
        };
    }

    private static String stageName(DamageStage stage) {
        return DamageStatsText.stage(stage).getString();
    }

    private static String confidenceName(AttributionConfidence confidence) {
        return DamageStatsText.confidence(confidence).getString();
    }

    private static boolean inside(double x, double y, int left, int right, int top, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (contextGroup == null) return;
        String[] options = {DamageStatsText.string("context.details"),
                DamageStatsText.string("context.annotate"), DamageStatsText.string("context.clear_annotation")};
        for (int i = 0; i < options.length; i++) {
            int top = contextY + i * 20;
            boolean hovered = inside(mouseX, mouseY, contextX, contextX + 128, top, top + 20);
            graphics.fill(contextX, top, contextX + 128, top + 19, hovered ? 0xF0606060 : 0xF0202020);
            graphics.drawString(font, options[i], contextX + 5, top + 6,
                    i == 2 && !ClientAnnotationStore.has(contextGroup.members.get(0).step) ? 0x777777 : 0xFFFFFF,
                    false);
        }
    }

    private int contextOption(double mouseX, double mouseY) {
        if (mouseX < contextX || mouseX >= contextX + 128 || mouseY < contextY || mouseY >= contextY + 60) {
            return -1;
        }
        int option = ((int) mouseY - contextY) / 20;
        if (option == 2 && !ClientAnnotationStore.has(contextGroup.members.get(0).step)) return -1;
        return option;
    }

    private void renderEffectIcon(GuiGraphics graphics, EffectPreview preview, int x, int y) {
        ResourceLocation id = ResourceLocation.tryParse(preview.effectId());
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) return;
        var effect = BuiltInRegistries.MOB_EFFECT.get(id);
        graphics.blit(x, y, 0, 18, 18, Minecraft.getInstance().getMobEffectTextures().get(effect));
    }

    private void renderEffectTooltip(GuiGraphics graphics, EffectPreview preview, int mouseX, int mouseY) {
        ResourceLocation id = ResourceLocation.tryParse(preview.effectId());
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) return;
        var effect = BuiltInRegistries.MOB_EFFECT.get(id);
        MobEffectInstance instance = new MobEffectInstance(effect, preview.duration(), preview.amplifier());
        List<Component> tooltip = new ArrayList<>();
        PotionUtils.addPotionTooltip(List.of(instance), tooltip, 1.0F);
        tooltip.add(DamageStatsText.text("tooltip.effect_id", preview.effectId())
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private enum SortMode { ACTUAL_ORDER, INSTANT_DELTA, REMOVAL_LOSS, MOD_GROUP }
    private record IndexedStep(int originalIndex, DamageStep step) { }
    private static final class StepGroup {
        private final GroupKey key;
        private final List<IndexedStep> members;
        private final boolean section;
        private final String sectionTitle;
        private final int sectionColor;
        private StepGroup(GroupKey key, List<IndexedStep> members) {
            this(key, members, false, "", 0xFFFFFF);
        }
        private StepGroup(GroupKey key, List<IndexedStep> members, boolean section,
                String sectionTitle, int sectionColor) {
            this.key = key;
            this.members = members;
            this.section = section;
            this.sectionTitle = sectionTitle;
            this.sectionColor = sectionColor;
        }
        private static StepGroup section(String title, int color) {
            return new StepGroup(null, List.of(), true, title, color);
        }
    }
    private record GroupKey(DamageStage stage, String modId, String kind, String id, String displayName,
            AttributionConfidence confidence, String operation, String evidence, boolean groupable, String unique) { }
}
