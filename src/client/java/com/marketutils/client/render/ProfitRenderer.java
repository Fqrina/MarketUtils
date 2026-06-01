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
            if (plainText.contains("Buy-It-Now:") || plainText.contains("BIN Price:") || plainText.contains("Buy it now:")) {
                parsedPrice = PriceParser.parsePrice(plainText.substring(plainText.indexOf(":") + 1));
            } else if (plainText.contains("Estimated Item Value:") || plainText.contains("Estimated Value:")) {
                estimatedValue = PriceParser.parsePrice(plainText.substring(plainText.indexOf(":") + 1));
            }
        }

        if (parsedPrice <= 0L || estimatedValue <= 0L) {
            return new ItemProfitInfo(parsedPrice, estimatedValue, 0);
        }

        long priceDifference = estimatedValue - parsedPrice;
        int color = calculateGradientColor(priceDifference);

        return new ItemProfitInfo(parsedPrice, estimatedValue, color);
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
}
