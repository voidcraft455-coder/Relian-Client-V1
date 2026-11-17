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
import org.joml.Matrix4f;

/** Renders the equipped local wardrobe PNGs as client-side decorative attachments. */
public final class CosmeticFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {

    public CosmeticFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                       AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player.isInvisible() || player != client.player) return;

        Identifier cape = CosmeticWardrobeRuntime.texture(client, "CAPE");
        if (cape != null) {
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(cape));
            matrices.push();
            getContextModel().body.rotate(matrices);
            drawQuad(matrices, buffer, light, -0.32f, -0.02f, 0.16f,
                    0.32f, 0.76f, 0.16f);
            matrices.pop();
        }

        Identifier wings = CosmeticWardrobeRuntime.texture(client, "WINGS");
        if (wings != null) {
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(wings));
            matrices.push();
            getContextModel().body.rotate(matrices);
            drawQuad(matrices, buffer, light, -0.78f, -0.06f, 0.13f,
                    -0.24f, 0.38f, 0.13f);
            drawQuad(matrices, buffer, light, 0.24f, -0.06f, 0.13f,
                    0.78f, 0.38f, 0.13f);
            matrices.pop();
        }

        Identifier hat = CosmeticWardrobeRuntime.texture(client, "HAT");
        if (hat != null) {
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(hat));
            matrices.push();
            getContextModel().head.rotate(matrices);
            drawQuad(matrices, buffer, light, -0.31f, -0.39f, -0.18f,
                    0.31f, -0.39f, 0.18f);
            drawQuad(matrices, buffer, light, -0.24f, -0.53f, -0.14f,
                    0.24f, -0.53f, 0.14f);
            matrices.pop();
        }
    }

    private static void drawQuad(MatrixStack matrices, VertexConsumer buffer, int light,
                                 float left, float top, float z,
                                 float right, float bottom, float z2) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = entry.getPositionMatrix();
        vertex(buffer, position, entry, left, top, z, 0, 0, light);
        vertex(buffer, position, entry, right, top, z, 1, 0, light);
        vertex(buffer, position, entry, right, bottom, z2, 1, 1, light);
        vertex(buffer, position, entry, left, bottom, z2, 0, 1, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f position, MatrixStack.Entry entry,
                               float x, float y, float z, float u, float v, int light) {
        buffer.vertex(position, x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
    }
}
