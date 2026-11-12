package com.reliant.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class HudEditorScreen extends Screen {
    private static final List<String> MODULES = List.of("fps", "coordinates", "keystrokes", "armor");
    
    public HudEditorScreen() {
        super(Text.literal("Relian HUD Editor"));
    }

    @Override
    protected void init() {
        clearChildren();
        int left = Math.max(18, (width - 500) / 2);
        int top = Math.max(18, (height - 310) / 2);
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(left + 410, top + 8, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset layout"), button -> {
            HudConfig.reset();
            init();
        }).dimensions(left + 290, top + 8, 110, 20).build());

        int y = top + 56;
        for (String id : MODULES) {
            HudConfig.ModuleSettings settings = HudConfig.get().modules.get(id);
            String label = id.toUpperCase();
            addDrawableChild(ButtonWidget.builder(Text.literal(label + (settings.enabled ? "  ON" : "  OFF")),
                button -> {
                    settings.enabled = !settings.enabled;
                    HudConfig.save();
                    init();
                }).dimensions(left + 10, y, 145, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("←"), button -> move(settings, -5, 0))
                .dimensions(left + 165, y, 25, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("→"), button -> move(settings, 5, 0))
                .dimensions(left + 193, y, 25, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("↑"), button -> move(settings, 0, -5))
                .dimensions(left + 221, y, 25, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("↓"), button -> move(settings, 0, 5))
                .dimensions(left + 249, y, 25, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Scale −"), button -> scale(settings, -0.1f))
                .dimensions(left + 280, y, 55, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Scale +"), button -> scale(settings, 0.1f))
                .dimensions(left + 338, y, 55, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Color"), button -> cycleColor(settings))\n                .dimensions(left + 397, y, 55, 20).build());\n            y += 34;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
            HudConfig.save();
            close();
        }).dimensions(left + 200, top + 270, 100, 22).build());
    }

    private void move(HudConfig.ModuleSettings settings, int dx, int dy) {
        settings.x = Math.max(0, Math.min(width - 20, settings.x + dx));
        settings.y = Math.max(0, Math.min(height - 20, settings.y + dy));
        HudConfig.save();
        init();
    }

    private void cycleColor(HudConfig.ModuleSettings settings) {\n        int[] palette = {0xFFFFFFFF, 0xFF83B7FF, 0xFF8FE3B0, 0xFFFFD479, 0xFFFF8E9E};\n        int next = 0;\n        for (int i = 0; i < palette.length; i++) if (palette[i] == settings.color) next = (i + 1) % palette.length;\n        settings.color = palette[next];\n        HudConfig.save();\n        init();\n    }\n\n    private void scale(HudConfig.ModuleSettings settings, float amount) {
        settings.scale = Math.max(0.5f, Math.min(2.0f, settings.scale + amount));
        HudConfig.save();
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = Math.max(18, (width - 500) / 2);
        int top = Math.max(18, (height - 310) / 2);
        context.fill(left, top, left + 500, top + 310, 0xE8171B24);
        context.drawTextWithShadow(textRenderer, Text.literal("RELIAN  /  HUD EDITOR"), left + 14, top + 16, 0xFF83B7FF);
        context.drawTextWithShadow(textRenderer,
            Text.literal("Right Shift toggles this editor. Toggle, reposition, resize and recolor each module."),
            left + 14, top + 38, 0xFFD7DCE5);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void close() {
        HudConfig.save();
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }
}
