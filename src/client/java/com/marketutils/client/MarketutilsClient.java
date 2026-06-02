package com.marketutils.client;

import com.marketutils.client.render.ProfitRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public class MarketutilsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipFlag, lines) -> {
			ProfitRenderer.appendWorthDebugText(itemStack, lines);
		});
	}
}