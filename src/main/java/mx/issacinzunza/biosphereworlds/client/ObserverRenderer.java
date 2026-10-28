package mx.issacinzunza.biosphereworlds.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import mx.issacinzunza.biosphereworlds.easteregg.ObserverEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Untextured camera-facing glow. No generated model, terrain light updates or shader dependency. */
public final class ObserverRenderer extends EntityRenderer<ObserverEntity> {
    public ObserverRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(ObserverEntity entity, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight) {
        if (!EasterEggConfig.ENABLED.get()) return;
        float brightness = entity.brightness(partialTick);
        Vec3 beam = entity.beam(partialTick);
        if (brightness <= 0 && beam.lengthSqr() == 0) return;
        poses.pushPose();
        var vertices = buffers.getBuffer(RenderType.lightning());
        if (beam.lengthSqr() > 0) beam(vertices, poses.last().pose(), beam);
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        var matrix = poses.last().pose();
        square(vertices, matrix, 3.0f, brightness * 0.09f);
        square(vertices, matrix, 1.35f, brightness * 0.30f);
        square(vertices, matrix, 0.64f, brightness);
        poses.popPose();
    }

    private static void beam(VertexConsumer vertices, Matrix4f matrix, Vec3 end) {
        Vec3 side = end.cross(new Vec3(0, 1, 0)).normalize().scale(0.10);
        if (side.lengthSqr() == 0) side = new Vec3(0.10, 0, 0);
        for (int plane = 0; plane < 2; plane++) {
            vertices.addVertex(matrix, (float) -side.x, (float) -side.y, (float) -side.z).setColor(205, 230, 255, 220);
            vertices.addVertex(matrix, (float) side.x, (float) side.y, (float) side.z).setColor(205, 230, 255, 220);
            vertices.addVertex(matrix, (float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(205, 230, 255, 220);
            vertices.addVertex(matrix, (float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(205, 230, 255, 220);
            side = end.cross(side).normalize().scale(0.10);
        }
    }

    private static void square(VertexConsumer vertices, Matrix4f matrix, float size, float alpha) {
        int a = Math.clamp((int) (alpha * 255), 0, 255);
        vertices.addVertex(matrix, -size, -size, 0).setColor(190, 225, 255, a);
        vertices.addVertex(matrix, size, -size, 0).setColor(190, 225, 255, a);
        vertices.addVertex(matrix, size, size, 0).setColor(190, 225, 255, a);
        vertices.addVertex(matrix, -size, size, 0).setColor(190, 225, 255, a);
    }

    @Override public ResourceLocation getTextureLocation(ObserverEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
