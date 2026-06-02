package com.marketutils.client.render;

import com.marketutils.client.util.PriceParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ProfitRenderer {
    private static final Map<ItemStack, ItemProfitInfo> PROFIT_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final long MAX_PROFIT_TINT_LIMIT = 2_000_000L;
    private static final int MINIMUM_ALPHA_TINT = 30;
    private static final int MAXIMUM_ALPHA_TINT = 85;

    private static final ThreadLocal<Boolean> EVALUATING_GUARD = ThreadLocal.withInitial(() -> false);

    private record ItemProfitInfo(long price, long estimatedValue, int tintColor) {}

    private ProfitRenderer() {
        // Prevent instantiation
    }

    public static void renderSlotBackground(GuiGraphics guiGraphics, Slot inventorySlot) {
        if (inventorySlot == null) {
            return;
        }

        ItemStack itemStack = inventorySlot.getItem();
        if (itemStack.isEmpty()) {
            return;
        }

        ItemProfitInfo info = PROFIT_CACHE.computeIfAbsent(itemStack, ProfitRenderer::evaluateItemProfit);
        if (info.tintColor() != 0) {
            int xStart = inventorySlot.x;
            int yStart = inventorySlot.y;
            guiGraphics.fill(xStart, yStart, xStart + 16, yStart + 16, info.tintColor());
        }
    }

    private static ItemProfitInfo evaluateItemProfit(ItemStack stack) {
        if (EVALUATING_GUARD.get()) {
            // Return dummy to break infinite recursion loop
            return new ItemProfitInfo(0L, 0L, 0);
        }

        EVALUATING_GUARD.set(true);
        try {
            Minecraft minecraftInstance = Minecraft.getInstance();
            if (minecraftInstance.level == null || minecraftInstance.player == null) {
                return new ItemProfitInfo(0L, 0L, 0);
            }

            List<Component> tooltipLines = stack.getTooltipLines(
                    Item.TooltipContext.of(minecraftInstance.level),
                    minecraftInstance.player,
                    TooltipFlag.Default.NORMAL
            );

            long parsedPrice = 0L;
            long estimatedValue = 0L;

            for (Component componentLine : tooltipLines) {
                String plainText = PriceParser.stripFormatting(componentLine.getString());
                String lowerLine = plainText.toLowerCase();

                int colonIndex = plainText.indexOf(":");
                if (colonIndex != -1) {
                    String valuePart = plainText.substring(colonIndex + 1);
                    if (lowerLine.contains("buy it now:") || lowerLine.contains("buy-it-now:") || lowerLine.contains("bin price:") || lowerLine.contains("bin:") || lowerLine.contains("starting bid:") || lowerLine.contains("current bid:")) {
                        parsedPrice = PriceParser.parsePrice(valuePart);
                    } else if (lowerLine.contains("estimated item value:") || lowerLine.contains("estimated value:")) {
                        estimatedValue = PriceParser.parsePrice(valuePart);
                    }
                }
            }

            if (parsedPrice <= 0L || estimatedValue <= 0L) {
                return new ItemProfitInfo(parsedPrice, estimatedValue, 0);
            }

            long priceDifference = estimatedValue - parsedPrice;
            int color = calculateGradientColor(priceDifference);

            return new ItemProfitInfo(parsedPrice, estimatedValue, color);
        } finally {
            EVALUATING_GUARD.set(false);
        }
    }

    private static int calculateGradientColor(long priceDifference) {
        if (priceDifference == 0L) {
            return 0;
        }

        double scalingFactor = Math.min(1.0, (double) Math.abs(priceDifference) / MAX_PROFIT_TINT_LIMIT);
        int alphaValue = MINIMUM_ALPHA_TINT + (int) ((MAXIMUM_ALPHA_TINT - MINIMUM_ALPHA_TINT) * scalingFactor);

        if (priceDifference > 0L) {
            // Safe vibrant green: alpha, red=0, green=180, blue=0
            return (alphaValue << 24) | (0 << 16) | (180 << 8) | 0;
        } else {
            // Safe vibrant red: alpha, red=180, green=0, blue=0
            return (alphaValue << 24) | (180 << 16) | (0 << 8) | 0;
        }
    }

    public static void appendWorthDebugText(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || lines == null) {
            return;
        }

        ItemProfitInfo info = PROFIT_CACHE.computeIfAbsent(stack, ProfitRenderer::evaluateItemProfit);
        if (info.price() > 0L && info.estimatedValue() > 0L) {
            long delta = info.estimatedValue() - info.price();
            if (delta > 0L) {
                lines.add(Component.literal("§aWorth it! Profit: +" + formatNumber(delta) + " coins"));
            } else if (delta < 0L) {
                lines.add(Component.literal("§cNot worth it! Loss: -" + formatNumber(Math.abs(delta)) + " coins"));
            } else {
                lines.add(Component.literal("§eNeutral! Value matches Price"));
            }
        }
    }

    private static String formatNumber(long number) {
        if (number >= 1_000_000_000L) {
            return String.format("%.2fB", number / 1_000_000_000.0);
        }
        if (number >= 1_000_000L) {
            return String.format("%.2fM", number / 1_000_000.0);
        }
        if (number >= 1_000L) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}
