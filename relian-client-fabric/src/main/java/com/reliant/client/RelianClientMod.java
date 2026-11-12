package com.reliant.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class RelianClientMod implements ClientModInitializer {
    private static KeyBinding openHudEditor;

    @Override
    public void onInitializeClient() {
        HudConfig.load();
        openHudEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.relian.open_hud_editor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "category.relian"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openHudEditor.wasPressed()) {
                if (client.currentScreen instanceof HudEditorScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new HudEditorScreen());
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
            RelianHudRenderer.render(MinecraftClient.getInstance(), drawContext)
        );
    }
}
