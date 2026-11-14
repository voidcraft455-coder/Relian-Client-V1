package com.reliant.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class HudEditorScreen extends Screen {
    private static final List<String> MODULES = List.of(
        "fps", "coordinates", "keystrokes", "armor", "ping", "held_item", "potions", "direction", "biome", "speed", "clock", "dimension", "target_block", "health", "hunger", "world_time", "server_info", "session_time"
    );
    private int selected = 0;
    private boolean draggingPreview = false;
    private int panelLeft;
    private int panelTop;

    public HudEditorScreen() {
        super(Text.literal("Relian HUD Editor"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        panelLeft = Math.max(12, (width - 700) / 2);
        panelTop = Math.max(12, (height - 510) / 2);
        rounded(context, panelLeft, panelTop, panelLeft + 700, panelTop + 510, 0xF0141A26, 12);
        rounded(context, panelLeft + 1, panelTop + 1, panelLeft + 699, panelTop + 54, 0xFF1B2638, 11);
        context.drawTextWithShadow(textRenderer, Text.literal("RELIAN"), panelLeft + 22, panelTop + 17, 0xFF83B7FF);
        context.drawTextWithShadow(textRenderer, Text.literal("HUD STUDIO"), panelLeft + 91, panelTop + 17, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Customize your on-screen modules"), panelLeft + 22, panelTop + 37, 0xFF9AA9BF);
        drawButton(context, panelLeft + 590, panelTop + 15, 88, 27, "CLOSE", 0xFF303C50, mouseX, mouseY);

        context.drawTextWithShadow(textRenderer, Text.literal("MODULES"), panelLeft + 22, panelTop + 69, 0xFF83B7FF);
        for (int i = 0; i < MODULES.size(); i++) {
            String id = MODULES.get(i);
            HudConfig.ModuleSettings settings = HudConfig.get().modules.get(id);
            int y = panelTop + 88 + i * 20;
            boolean active = i == selected;
            rounded(context, panelLeft + 18, y, panelLeft + 402, y + 18,
                active ? 0xFF26364D : 0xFF1C2635, 9);
            context.drawTextWithShadow(textRenderer, Text.literal(label(id)), panelLeft + 30, y + 5,
                active ? 0xFFFFFFFF : 0xFFD8E0EB);
            context.drawTextWithShadow(textRenderer,
                Text.literal(settings.enabled ? "ON" : "OFF"), panelLeft + 190, y + 5,
                settings.enabled ? 0xFF7EE0B0 : 0xFF8995A7);
            drawButton(context, panelLeft + 265, y + 2, 62, 20, settings.enabled ? "ON" : "OFF",
                settings.enabled ? 0xFF245A4C : 0xFF465164, mouseX, mouseY);
            drawButton(context, panelLeft + 333, y + 2, 57, 20, "EDIT", 0xFF34445B, mouseX, mouseY);
        }

        int px = panelLeft + 425;
        int py = panelTop + 88;
        rounded(context, px, py, px + 255, py + 282, 0xFF101722, 12);
        context.drawTextWithShadow(textRenderer, Text.literal("LIVE HUD PREVIEW"), px + 14, py + 13, 0xFF83B7FF);
        rounded(context, px + 10, py + 36, px + 245, py + 235, 0xFF263342, 9);
        context.drawTextWithShadow(textRenderer, Text.literal("Selected module preview"), px + 22, py + 49, 0xFF71839A);
        HudConfig.ModuleSettings previewSettings = HudConfig.get().modules.get(MODULES.get(selected));
        String sample = previewText(MODULES.get(selected));
        int previewX = px + 14 + (int) (previewSettings.x * 207.0 / Math.max(1, width));
        int previewY = py + 62 + (int) (previewSettings.y * 158.0 / Math.max(1, height));
        previewX = Math.max(px + 14, Math.min(px + 225, previewX));
        previewY = Math.max(py + 62, Math.min(py + 218, previewY));
        context.getMatrices().push();
        context.getMatrices().translate(previewX, previewY, 0);
        context.getMatrices().scale(previewSettings.scale, previewSettings.scale, 1.0f);
        context.drawTextWithShadow(textRenderer, Text.literal(previewSettings.enabled ? sample : sample + " (hidden)"),
            0, 0, previewSettings.enabled ? previewSettings.color : 0xFF8995A7);
        context.getMatrices().pop();
        rounded(context, px + 10, py + 244, px + 245, py + 271, 0xFF24344A, 8);
        context.drawTextWithShadow(textRenderer, Text.literal("Drag preview or use arrow keys to move"),
            px + 18, py + 253, 0xFFB9C7D8);

        HudConfig.ModuleSettings chosen = HudConfig.get().modules.get(MODULES.get(selected));
        context.drawTextWithShadow(textRenderer, Text.literal("SELECTED: " + label(MODULES.get(selected))),
            px, py + 296, 0xFFDCE6F3);
        context.drawTextWithShadow(textRenderer,
            Text.literal("Position  " + chosen.x + ", " + chosen.y + "     Scale  " + String.format("%.1f", chosen.scale)),
            px, py + 313, 0xFF9AA9BF);
        drawButton(context, px, py + 336, 73, 27, "COLOR", 0xFF34445B, mouseX, mouseY);
        drawButton(context, px + 80, py + 336, 73, 27, "SIZE −", 0xFF34445B, mouseX, mouseY);
        drawButton(context, px + 160, py + 336, 73, 27, "SIZE +", 0xFF34445B, mouseX, mouseY);

        drawButton(context, panelLeft + 18, panelTop + 470, 104, 25, "RESET", 0xFF4B3540, mouseX, mouseY);
        drawButton(context, panelLeft + 134, panelTop + 470, 104, 25, "SAVE", 0xFF245A4C, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, Text.literal("RIGHT SHIFT  •  CLOSE"), panelLeft + 490,
            panelTop + 480, 0xFF7E8DA2);
    }

    private String previewText(String id) {
        return switch (id) {
            case "fps" -> "FPS: 240";
            case "coordinates" -> "XYZ: 124 / 68 / -32";
            case "keystrokes" -> "[W]  A  S  D";
            case "armor" -> "ARMOR: 98%  91%  84%";
            case "ping" -> "PING: 34 ms";
            case "held_item" -> "HELD ITEM: 92%";
            case "potions" -> "SPEED II  1:24";
            case "direction" -> "FACING: NORTH";
            case "biome" -> "BIOME: PLAINS";
            case "speed" -> "SPEED: 4.32 blocks/s";
            case "clock" -> "LOCAL TIME: 09:30";
            case "dimension" -> "DIMENSION: OVERWORLD";
            case "target_block" -> "LOOKING AT: STONE";
            case "health" -> "HEALTH: 20.0 / 20.0";
            case "hunger" -> "FOOD: 20 / 20  SAT: 5.0";
            case "world_time" -> "WORLD TIME: 12:00  DAY 1";
            case "server_info" -> "SERVER: Singleplayer";
            case "session_time" -> "SESSION: 00:12:34";
            default -> label(id);
        };
    }

    private void drawButton(DrawContext context, int x, int y, int w, int h, String label,
                            int color, int mouseX, int mouseY) {
        int fill = inside(mouseX, mouseY, x, y, w, h) ? brighten(color) : color;
        rounded(context, x, y, x + w, y + h, fill, 7);
        int textWidth = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, Text.literal(label), x + (w - textWidth) / 2,
            y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private int brighten(int color) {
        return 0xFF000000 | Math.min(255, ((color >> 16) & 255) + 18) << 16
            | Math.min(255, ((color >> 8) & 255) + 18) << 8
            | Math.min(255, (color & 255) + 18);
    }

    private void rounded(DrawContext context, int x1, int y1, int x2, int y2, int color, int radius) {
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

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String label(String id) {
        return switch (id) {
            case "fps" -> "FPS COUNTER";
            case "coordinates" -> "COORDINATES";
            case "keystrokes" -> "KEYSTROKES";
            case "armor" -> "ARMOR STATUS";
            case "ping" -> "PING DISPLAY";
            case "held_item" -> "HELD ITEM DURABILITY";
            case "potions" -> "POTION EFFECTS";
            case "direction" -> "DIRECTION";
            case "biome" -> "BIOME DISPLAY";
            case "speed" -> "MOVEMENT SPEED";
            case "clock" -> "LOCAL CLOCK";
            case "dimension" -> "DIMENSION";
            case "target_block" -> "TARGET BLOCK";
            case "health" -> "HEALTH DISPLAY";
            case "hunger" -> "HUNGER / SATURATION";
            case "world_time" -> "WORLD CLOCK / DAY";
            case "server_info" -> "SERVER / PLAYER COUNT";
            case "session_time" -> "SESSION TIMER";
            default -> id.toUpperCase();
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int px = panelLeft + 425;
        int py = panelTop + 88;
        for (int i = 0; i < MODULES.size(); i++) {
            int y = panelTop + 88 + i * 20;
            if (inside(mouseX, mouseY, panelLeft + 18, y, 384, 18)) {
                selected = i;
                if (inside(mouseX, mouseY, panelLeft + 265, y + 1, 62, 16)) {
                    HudConfig.get().modules.get(MODULES.get(i)).enabled =
                        !HudConfig.get().modules.get(MODULES.get(i)).enabled;
                    HudConfig.save();
                }
                return true;
            }
        }
        if (inside(mouseX, mouseY, panelLeft + 590, panelTop + 15, 88, 27)) {
            closeEditor();
            return true;
        }
        if (inside(mouseX, mouseY, panelLeft + 18, panelTop + 470, 104, 25)) {
            HudConfig.reset();
            return true;
        }
        if (inside(mouseX, mouseY, panelLeft + 134, panelTop + 470, 104, 25)) {
            HudConfig.save();
            closeEditor();
            return true;
        }
        HudConfig.ModuleSettings settings = HudConfig.get().modules.get(MODULES.get(selected));
        if (inside(mouseX, mouseY, px, py + 336, 73, 27)) {
            cycleColor(settings);
            return true;
        }
        if (inside(mouseX, mouseY, px + 80, py + 336, 73, 27)) {
            settings.scale = Math.max(0.5f, settings.scale - 0.1f);
            HudConfig.save();
            return true;
        }
        if (inside(mouseX, mouseY, px + 160, py + 336, 73, 27)) {
            settings.scale = Math.min(2.0f, settings.scale + 0.1f);
            HudConfig.save();
            return true;
        }
        if (inside(mouseX, mouseY, px + 10, py + 36, 235, 199)) {
            draggingPreview = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingPreview && button == 0) {
            HudConfig.ModuleSettings settings = HudConfig.get().modules.get(MODULES.get(selected));
            double previewX = mouseX - (panelLeft + 435);
            double previewY = mouseY - (panelTop + 124);
            settings.x = Math.max(0, Math.min(width - 20, (int) Math.round(previewX * width / 235.0)));
            settings.y = Math.max(0, Math.min(height - 20, (int) Math.round(previewY * height / 199.0)));
            HudConfig.save();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void cycleColor(HudConfig.ModuleSettings settings) {
        int[] palette = {0xFFFFFFFF, 0xFF83B7FF, 0xFF8FE3B0, 0xFFFFD479, 0xFFFF8E9E};
        int next = 0;
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == settings.color) {
                next = (i + 1) % palette.length;
                break;
            }
        }
        settings.color = palette[next];
        HudConfig.save();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            closeEditor();
            return true;
        }
        int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 2;
        HudConfig.ModuleSettings settings = HudConfig.get().modules.get(MODULES.get(selected));
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) settings.x = Math.max(0, settings.x - step);
            if (keyCode == GLFW.GLFW_KEY_RIGHT) settings.x = Math.min(Math.max(0, width - 1), settings.x + step);
            if (keyCode == GLFW.GLFW_KEY_UP) settings.y = Math.max(0, settings.y - step);
            if (keyCode == GLFW.GLFW_KEY_DOWN) settings.y = Math.min(Math.max(0, height - 1), settings.y + step);
            HudConfig.save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void closeEditor() {
        HudConfig.save();
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }
}
