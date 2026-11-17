package com.reliant.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Simple client-side 3D wardrobe attachments. They are local visuals, not server-synced cosmetics. */
public final class CosmeticFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier WHITE = Identifier.of("minecraft", "textures/block/white_wool.png");

    public CosmeticFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                       AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (player.isInvisible() || player != MinecraftClient.getInstance().player) return;
        var equipped = CosmeticWardrobeRuntime.lines();
        if (equipped.isEmpty()) return;
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));

        if (equipped.stream().anyMatch(line -> line.startsWith("CAPE:"))) {
            matrices.push();
            getContextModel().body.rotate(matrices);
            drawQuad(matrices, buffer, light, -0.32f, -0.02f, 0.16f,
                    0.32f, 0.76f, 0.16f, 0xFF477BCB);
            matrices.pop();
        }

        if (equipped.stream().anyMatch(line -> line.startsWith("WINGS:"))) {
            matrices.push();
            getContextModel().body.rotate(matrices);
            drawQuad(matrices, buffer, light, -0.78f, -0.06f, 0.13f,
                    -0.24f, 0.38f, 0.13f, 0xFF9B75E8);
            drawQuad(matrices, buffer, light, 0.24f, -0.06f, 0.13f,
                    0.78f, 0.38f, 0.13f, 0xFF9B75E8);
            matrices.pop();
        }

        if (equipped.stream().anyMatch(line -> line.startsWith("HAT:"))) {
            matrices.push();
            getContextModel().head.rotate(matrices);
            drawQuad(matrices, buffer, light, -0.31f, -0.39f, -0.18f,
                    0.31f, -0.39f, 0.18f, 0xFFFFC85C);
            drawQuad(matrices, buffer, light, -0.24f, -0.53f, -0.14f,
                    0.24f, -0.53f, 0.14f, 0xFFFFC85C);
            matrices.pop();
        }
    }

    private static void drawQuad(MatrixStack matrices, VertexConsumer buffer, int light,
                                 float left, float top, float z,
                                 float right, float bottom, float z2, int color) {
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        int red = (color >>> 16) & 255;
        int green = (color >>> 8) & 255;
        int blue = color & 255;
        vertex(buffer, position, normal, left, top, z, 0, 0, red, green, blue, light);
        vertex(buffer, position, normal, right, top, z, 1, 0, red, green, blue, light);
        vertex(buffer, position, normal, right, bottom, z2, 1, 1, red, green, blue, light);
        vertex(buffer, position, normal, left, bottom, z2, 0, 1, red, green, blue, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int light) {
        buffer.vertex(position, x, y, z).color(red, green, blue, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0, 0, 1);
    }
}
