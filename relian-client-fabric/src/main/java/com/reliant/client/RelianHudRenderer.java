package com.reliant.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

final class RelianHudRenderer {
    private RelianHudRenderer() {}

    static void render(MinecraftClient client, DrawContext context) {
        if (client.player == null || client.options.hudHidden || client.currentScreen instanceof HudEditorScreen) return;
        HudConfig.Config config = HudConfig.get();
        renderText(client, context, config, "fps", List.of("FPS: " + client.getCurrentFps()));
        if (client.player != null) {
            renderText(client, context, config, "coordinates", List.of(
                "XYZ: " + client.player.getBlockX() + " / " + client.player.getBlockY() + " / " + client.player.getBlockZ()
            ));
            renderKeystrokes(client, context, config);
            renderArmor(client, context, config);
        }
    }

    private static void renderKeystrokes(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        List<String> lines = List.of(
            (client.options.forwardKey.isPressed() ? "[W]" : " W ") ,
            (client.options.leftKey.isPressed() ? "[A]" : " A ") + " " +
                (client.options.backKey.isPressed() ? "[S]" : " S ") + " " +
                (client.options.rightKey.isPressed() ? "[D]" : " D "),
            client.options.jumpKey.isPressed() ? "[SPACE]" : " SPACE "
        );
        renderText(client, context, config, "keystrokes", lines);
    }

    private static void renderArmor(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        List<String> lines = new ArrayList<>();
        for (ItemStack stack : client.player.getInventory().armor) {
            if (stack.isEmpty()) continue;
            if (stack.isDamageable()) {
                int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
                int percent = MathHelper.floor(remaining * 100.0f / stack.getMaxDamage());
                lines.add(stack.getName().getString() + ": " + percent + "%");
            } else {
                lines.add(stack.getName().getString());
            }
        }
        if (lines.isEmpty()) lines.add("Armor: empty");
        renderText(client, context, config, "armor", lines);
    }

    private static void renderText(MinecraftClient client, DrawContext context, HudConfig.Config config,
                                   String id, List<String> lines) {
        HudConfig.ModuleSettings settings = config.modules.get(id);
        if (settings == null || !settings.enabled) return;
        float scale = Math.max(0.5f, Math.min(2.0f, settings.scale));
        context.getMatrices().push();
        context.getMatrices().translate(settings.x, settings.y, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        int width = 0;
        for (String line : lines) width = Math.max(width, client.textRenderer.getWidth(line));
        int height = lines.size() * 11;
        context.fill(-3, -2, width + 4, height, 0x660D1119);
        int y = 0;
        for (String line : lines) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), 0, y, settings.color);
            y += 11;
        }
        context.getMatrices().pop();
    }
}
