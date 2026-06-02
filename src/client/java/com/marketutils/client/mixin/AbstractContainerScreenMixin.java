package com.marketutils.client.mixin;

import com.marketutils.client.render.ProfitRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void injectProfitOverlay(GuiGraphics guiGraphics, Slot inventorySlot, CallbackInfo callbackInfo) {
        if (inventorySlot == null) {
            return;
        }

        Screen screenInstance = (Screen) (Object) this;
        Component titleComponent = screenInstance.getTitle();
        if (titleComponent == null) {
            return;
        }

        String screenTitle = titleComponent.getString();
        if (screenTitle.contains("Auctions") || screenTitle.contains("Auction") || screenTitle.contains("BIN")) {
            // Only process slots that do not belong to the player's personal inventory
            if (inventorySlot.container != null && !(inventorySlot.container instanceof Inventory)) {
                ProfitRenderer.renderSlotBackground(guiGraphics, inventorySlot);
            }
        }
    }
}
