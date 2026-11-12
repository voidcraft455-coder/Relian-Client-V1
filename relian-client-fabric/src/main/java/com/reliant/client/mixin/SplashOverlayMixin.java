package com.reliant.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void relian$drawBrand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        context.fill(0, 0, width, height, 0x990B1019);

        // Original Relian geometric monogram: a block-built R, not another client's logo.
        int left = centerX - 25;
        int top = centerY - 66;
        int blue = 0xFF75A9F4;
        context.fill(left, top, left + 7, top + 48, blue);
        context.fill(left + 7, top, left + 36, top + 7, blue);
        context.fill(left + 29, top + 7, left + 36, top + 23, blue);
        context.fill(left + 7, top + 21, left + 29, top + 28, blue);
        context.fill(left + 20, top + 28, left + 28, top + 35, blue);
        context.fill(left + 28, top + 35, left + 36, top + 48, blue);

        String title = "RELIAN CLIENT";
        String subtitle = "LOADING MINECRAFT";
        int titleWidth = client.textRenderer.getWidth(title);
        int subtitleWidth = client.textRenderer.getWidth(subtitle);
        context.drawTextWithShadow(client.textRenderer, Text.literal(title),
            centerX - titleWidth / 2, centerY - 8, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, Text.literal(subtitle),
            centerX - subtitleWidth / 2, centerY + 7, 0xFFB8C5D8);
    }
}
