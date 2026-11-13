package com.reliant.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
        renderPotions(client, context, config);
        renderDirection(client, context, config);
        renderBiome(client, context, config);
        renderSpeed(client, context, config);
        renderClock(client, context, config);
        renderDimension(client, context, config);
        renderTargetBlock(client, context, config);
        renderHealth(client, context, config);
        renderHunger(client, context, config);
        renderWorldTime(client, context, config);
        renderServerInfo(client, context, config);
        renderSessionTime(client, context, config);
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


    private static void renderPotions(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        List<String> lines = new ArrayList<>();
        for (var effect : client.player.getStatusEffects()) {
            String name = effect.getEffectType().value().getName().getString();
            int seconds = Math.max(0, effect.getDuration() / 20);
            String duration = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
            int level = effect.getAmplifier() + 1;
            lines.add(name + (level > 1 ? " " + level : "") + " • " + duration);
        }
        if (lines.isEmpty()) lines.add("No active effects");
        renderText(client, context, config, "potions", lines);
    }

    private static void renderDirection(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        String direction = client.player.getHorizontalFacing().asString().toUpperCase(java.util.Locale.ROOT);
        renderText(client, context, config, "direction", List.of("FACING: " + direction));
    }

    private static void renderBiome(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        String biome = client.world.getBiome(client.player.getBlockPos()).getKey()
            .map(key -> key.getValue().getPath().replace('_', ' '))
            .orElse("unknown");
        renderText(client, context, config, "biome", List.of("BIOME: " + biome));
    }

    private static void renderSpeed(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        double blocksPerSecond = client.player.getVelocity().horizontalLength() * 20.0;
        renderText(client, context, config, "speed",
            List.of(String.format(java.util.Locale.ROOT, "SPEED: %.2f blocks/s", blocksPerSecond)));
    }

    private static void renderClock(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        renderText(client, context, config, "clock",
            List.of("LOCAL TIME: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))));
    }

    private static void renderDimension(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        String dimension = client.world.getRegistryKey().getValue().getPath().replace('_', ' ');
        renderText(client, context, config, "dimension", List.of("DIMENSION: " + dimension));
    }

    private static void renderTargetBlock(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            String block = client.world.getBlockState(hit.getBlockPos()).getBlock().getName().getString();
            renderText(client, context, config, "target_block", List.of("LOOKING AT: " + block));
        } else {
            renderText(client, context, config, "target_block", List.of("LOOKING AT: —"));
        }
    }


    private static long sessionStartedAt;
    private static void renderHealth(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        float health = client.player.getHealth();
        float maxHealth = client.player.getMaxHealth();
        renderText(client, context, config, "health", List.of(
            String.format(java.util.Locale.ROOT, "HEALTH: %.1f / %.1f", health, maxHealth)));
    }

    private static void renderHunger(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        var hunger = client.player.getHungerManager();
        renderText(client, context, config, "hunger", List.of(String.format(
            java.util.Locale.ROOT, "FOOD: %d / 20  SAT: %.1f", hunger.getFoodLevel(), hunger.getSaturationLevel())));
    }

    private static void renderWorldTime(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        long ticks = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        int hour = (int) ((ticks / 1000 + 6) % 24);
        int minute = (int) ((ticks % 1000) * 60 / 1000);
        long day = Math.max(1, client.world.getTime() / 24000 + 1);
        renderText(client, context, config, "world_time", List.of(String.format(
            java.util.Locale.ROOT, "WORLD TIME: %02d:%02d  DAY %d", hour, minute, day)));
    }

    private static void renderServerInfo(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        String server = client.isInSingleplayer() ? "Singleplayer" :
            client.getCurrentServerEntry() == null ? "Multiplayer" : client.getCurrentServerEntry().address;
        int players = client.getNetworkHandler() == null ? 1 : client.getNetworkHandler().getPlayerList().size();
        renderText(client, context, config, "server_info", List.of("SERVER: " + server, "PLAYERS: " + players));
    }

    private static void renderSessionTime(MinecraftClient client, DrawContext context, HudConfig.Config config) {
        if (sessionStartedAt == 0L) sessionStartedAt = System.currentTimeMillis();
        long seconds = Math.max(0, (System.currentTimeMillis() - sessionStartedAt) / 1000);
        renderText(client, context, config, "session_time", List.of(String.format(
            java.util.Locale.ROOT, "SESSION: %02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)));
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
