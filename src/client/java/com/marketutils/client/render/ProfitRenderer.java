package com.marketutils.client.render;

import com.marketutils.client.util.PriceParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates whether auction items are worth buying by comparing listing price
 * to SkyHanni's estimated value. Renders a green/red tint behind slots and
 * appends debug text to tooltips.
 *
 * Architecture:
 *   - renderSlotBackground() is called from the renderSlot mixin every frame.
 *     It reads from a slot-indexed cache and draws the tint. Evaluations are
 *     throttled to a few per frame to avoid FPS drops.
 *   - appendTooltipText() is called from the ItemTooltipCallback. It parses
 *     the already-assembled tooltip lines (no getTooltipLines call, zero
 *     recursion risk) and appends a debug line.
 */
public final class ProfitRenderer {

    private static final long MAX_PROFIT_TINT_LIMIT = 2_000_000L;
    private static final int MIN_ALPHA = 40;
    private static final int MAX_ALPHA = 100;

    private static final int MAX_EVALUATIONS_PER_FRAME = 3;
    private static int evaluationsThisFrame = 0;
    private static long lastFrameStartNanos = 0;

    /**
     * Recursion guard. Set to true while evaluateFromTooltip is running
     * (which calls getTooltipLines, which fires ItemTooltipCallback,
     * which calls appendTooltipText). The guard prevents re-entrant
     * evaluation in both public entry points.
     */
    private static boolean evaluating = false;

    /**
     * Cache keyed by slot index within the container. Using slot index instead
     * of ItemStack identity because some container implementations return
     * different ItemStack references per call, which breaks identity-based
     * caching. Each entry also stores a fingerprint so stale entries (from
     * page navigation) are detected and re-evaluated.
     */
    private static final Map<Integer, SlotProfitEntry> SLOT_CACHE = new HashMap<>();

    private record SlotProfitEntry(
            String fingerprint,
            long price,
            long estimatedValue,
            int tintColor
    ) {}

    private ProfitRenderer() {}

    /**
     * Called from the renderSlot mixin for each non-player slot on auction
     * screens. Draws a translucent green/red rectangle behind the item.
     *
     * Evaluations of uncached slots are throttled to MAX_EVALUATIONS_PER_FRAME
     * to prevent all 54 slots from being evaluated in a single frame (which
     * would spike the frame time and drop FPS).
     */
    public static void renderSlotBackground(GuiGraphics guiGraphics, Slot slot) {
        if (slot == null) {
            return;
        }

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }

        if (evaluating) {
            return;
        }

        resetFrameCounterIfNewFrame();

        int slotIndex = slot.index;
        String fingerprint = buildFingerprint(stack);
        SlotProfitEntry cached = SLOT_CACHE.get(slotIndex);

        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            renderTint(guiGraphics, slot, cached.tintColor());
            return;
        }

        if (evaluationsThisFrame >= MAX_EVALUATIONS_PER_FRAME) {
            return;
        }

        evaluating = true;
        try {
            SlotProfitEntry entry = evaluateFromTooltip(stack, fingerprint);
            SLOT_CACHE.put(slotIndex, entry);
            evaluationsThisFrame++;
            renderTint(guiGraphics, slot, entry.tintColor());
        } finally {
            evaluating = false;
        }
    }

    /**
     * Called from the ItemTooltipCallback registered in MarketutilsClient.
     * Parses the tooltip lines that Minecraft and other mods have already
     * assembled -- no getTooltipLines() call, so there is zero recursion
     * risk and negligible performance cost.
     *
     * Always appends a "[MarketUtils]" tag for visibility during debugging.
     */
    public static void appendTooltipText(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || lines == null) {
            return;
        }

        if (evaluating) {
            return;
        }

        lines.add(Component.literal("\u00A77[MarketUtils] Active"));

        long price = 0L;
        long estimatedValue = 0L;

        for (Component line : lines) {
            String plain = PriceParser.stripFormatting(line.getString());
            String lower = plain.toLowerCase();
            int colon = plain.indexOf(':');
            if (colon == -1) {
                continue;
            }

            String afterColon = plain.substring(colon + 1);

            if (isListingPriceLabel(lower)) {
                long parsed = PriceParser.parsePrice(afterColon);
                if (parsed > 0L) {
                    price = parsed;
                }
            } else if (isEstimatedValueLabel(lower)) {
                long parsed = PriceParser.parsePrice(afterColon);
                if (parsed > 0L) {
                    estimatedValue = parsed;
                }
            }
        }

        if (price > 0L && estimatedValue > 0L) {
            long delta = estimatedValue - price;
            String text;
            if (delta > 0L) {
                text = "\u00A7aWorth it! Profit: +" + formatNumber(delta) + " coins";
            } else if (delta < 0L) {
                text = "\u00A7cNot worth it! Loss: " + formatNumber(delta) + " coins";
            } else {
                text = "\u00A7eBreak-even";
            }
            lines.add(Component.literal(text));
        }
    }

    public static void clearCache() {
        SLOT_CACHE.clear();
    }

    // -- Internal evaluation --

    /**
     * Builds the full tooltip for a stack (triggering all mod callbacks with
     * the guard set, so our own callback is a no-op) and parses price/value
     * from the resulting lines.
     */
    private static SlotProfitEntry evaluateFromTooltip(ItemStack stack, String fingerprint) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return new SlotProfitEntry(fingerprint, 0L, 0L, 0);
        }

        List<Component> tooltipLines = stack.getTooltipLines(
                Item.TooltipContext.of(mc.level),
                mc.player,
                TooltipFlag.Default.NORMAL
        );

        long price = 0L;
        long estimatedValue = 0L;

        for (Component line : tooltipLines) {
            String plain = PriceParser.stripFormatting(line.getString());
            String lower = plain.toLowerCase();
            int colon = plain.indexOf(':');
            if (colon == -1) {
                continue;
            }

            String afterColon = plain.substring(colon + 1);

            if (isListingPriceLabel(lower)) {
                long parsed = PriceParser.parsePrice(afterColon);
                if (parsed > 0L) {
                    price = parsed;
                }
            } else if (isEstimatedValueLabel(lower)) {
                long parsed = PriceParser.parsePrice(afterColon);
                if (parsed > 0L) {
                    estimatedValue = parsed;
                }
            }
        }

        if (price <= 0L || estimatedValue <= 0L) {
            return new SlotProfitEntry(fingerprint, price, estimatedValue, 0);
        }

        long delta = estimatedValue - price;
        int color = computeTintColor(delta);
        return new SlotProfitEntry(fingerprint, price, estimatedValue, color);
    }

    // -- Label matching --

    private static boolean isListingPriceLabel(String lowerLine) {
        return lowerLine.contains("buy it now:")
                || lowerLine.contains("starting bid:")
                || lowerLine.contains("current bid:")
                || lowerLine.contains("top bid:")
                || lowerLine.contains("bin price:")
                || lowerLine.contains("buy-it-now:")
                || lowerLine.contains("price:");
    }

    private static boolean isEstimatedValueLabel(String lowerLine) {
        return lowerLine.contains("estimated item value:")
                || lowerLine.contains("estimated value:")
                || lowerLine.contains("est. value:")
                || lowerLine.contains("item value:");
    }

    // -- Rendering helpers --

    private static void renderTint(GuiGraphics guiGraphics, Slot slot, int tintColor) {
        if (tintColor == 0) {
            return;
        }
        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tintColor);
    }

    private static int computeTintColor(long priceDifference) {
        if (priceDifference == 0L) {
            return 0;
        }

        double scale = Math.min(1.0, (double) Math.abs(priceDifference) / MAX_PROFIT_TINT_LIMIT);
        int alpha = MIN_ALPHA + (int) ((MAX_ALPHA - MIN_ALPHA) * scale);

        if (priceDifference > 0L) {
            return (alpha << 24) | (0x00B400);
        } else {
            return (alpha << 24) | (0xB40000);
        }
    }

    // -- Frame throttling --

    private static void resetFrameCounterIfNewFrame() {
        long now = System.nanoTime();
        if (now - lastFrameStartNanos > 1_000_000L) {
            evaluationsThisFrame = 0;
            lastFrameStartNanos = now;
        }
    }

    // -- Fingerprinting --

    /**
     * Produces a lightweight string that changes when the item in a slot
     * changes (e.g., AH page navigation). Uses the display name, which is
     * stable within a single page but different across items.
     */
    private static String buildFingerprint(ItemStack stack) {
        return stack.getDisplayName().getString();
    }

    // -- Number formatting --

    private static String formatNumber(long number) {
        long absolute = Math.abs(number);
        String sign = number < 0 ? "-" : "+";
        if (absolute >= 1_000_000_000L) {
            return sign + String.format("%.2fB", absolute / 1_000_000_000.0);
        }
        if (absolute >= 1_000_000L) {
            return sign + String.format("%.2fM", absolute / 1_000_000.0);
        }
        if (absolute >= 1_000L) {
            return sign + String.format("%.1fK", absolute / 1_000.0);
        }
        return sign + absolute;
    }
}
