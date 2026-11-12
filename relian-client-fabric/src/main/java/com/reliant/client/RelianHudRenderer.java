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
        renderText(client, context, config, "coordinates", List.of(
            "XYZ: " + client.player.getBlockX() + " / " + client.player.getBlockY() + " / " + client.player.getBlockZ()
        ));
        renderKeystrokes(client, context, config);
        renderArmor(client, context, config);
        renderPing(client, context, config);
        renderHeldItem(client, context, config);
    }

    private static void renderKeystrokes(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        List<String> lines = List.of(
            client.options.forwardKey.isPressed() ? "[W]" : " W ",
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

    private static void renderPing(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        if (client.getNetworkHandler() == null || client.player == null) return;
        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry != null) renderText(client, context, config, "ping", List.of("PING: " + entry.getLatency() + " ms"));
    }

    private static void renderHeldItem(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) {
            renderText(client, context, config, "held_item", List.of("HELD ITEM: empty"));
        } else if (stack.isDamageable()) {
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
            int percent = MathHelper.floor(remaining * 100.0f / stack.getMaxDamage());
            renderText(client, context, config, "held_item", List.of("HELD ITEM: " + percent + "%"));
        } else {
            renderText(client, context, config, "held_item", List.of("HELD ITEM: " + stack.getName().getString()));
        }
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
        rounded(context, -5, -4, width + 7, height + 3, 0x99101824, 5);
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(lines.get(i)), 0, i * 11 + 1, settings.color);
        }
        context.getMatrices().pop();
    }

    private static void rounded(DrawContext context, int x1, int y1, int x2, int y2, int color, int radius) {
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        for (int y = y1; y < y2; y++) {
            int dy = y < y1 + r ? y1 + r - y : (y >= y2 - r ? y - (y2 - r - 1) : 0);
            int inset = 0;
            if (dy > 0) {
                double inside = (double) r * r - (double) (r - dy) * (r - dy);
                inset = r - (int) Math.sqrt(Math.max(0, inside));
            }
            context.fill(x1 + inset, y, x2 - inset, y + 1, color);
        }
    }
}
