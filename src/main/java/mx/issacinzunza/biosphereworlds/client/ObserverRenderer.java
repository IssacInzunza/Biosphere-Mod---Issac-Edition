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
        boolean hostile = entity.hostile();
        if (beam.lengthSqr() > 0) {
            beam(vertices, poses.last().pose(), beam, 0.90, 45, hostile);
            beam(vertices, poses.last().pose(), beam, 0.25, 240, hostile);
            // The crossed beam is edge-on when aiming at the camera. A flare slightly
            // before its endpoint remains visible outside the first-person near plane.
            poses.pushPose();
            Vec3 flare = beam.subtract(beam.normalize().scale(Math.min(1.5, beam.length() * 0.5)));
            poses.translate(flare.x, flare.y, flare.z);
            poses.mulPose(entityRenderDispatcher.cameraOrientation());
            square(vertices, poses.last().pose(), 0.75f, 0.18f, hostile);
            square(vertices, poses.last().pose(), 0.18f, 0.95f, hostile);
            poses.popPose();
        }
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        var matrix = poses.last().pose();
        square(vertices, matrix, 3.0f, brightness * 0.09f, hostile);
        square(vertices, matrix, 1.35f, brightness * 0.30f, hostile);
        square(vertices, matrix, 0.64f, brightness, hostile);
        poses.popPose();
    }

    private static void beam(VertexConsumer vertices, Matrix4f matrix, Vec3 end, double width, int alpha, boolean hostile) {
        Vec3 side = end.cross(new Vec3(0, 1, 0)).normalize().scale(width);
        if (side.lengthSqr() == 0) side = new Vec3(width, 0, 0);
        int red = hostile ? 255 : 205, green = hostile ? 100 : 230, blue = hostile ? 55 : 255;
        for (int plane = 0; plane < 2; plane++) {
            vertices.addVertex(matrix, (float) -side.x, (float) -side.y, (float) -side.z).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, (float) side.x, (float) side.y, (float) side.z).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, (float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
            vertices.addVertex(matrix, (float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
            side = end.cross(side).normalize().scale(width);
        }
    }

    private static void square(VertexConsumer vertices, Matrix4f matrix, float size, float alpha, boolean hostile) {
        int a = Math.clamp((int) (alpha * 255), 0, 255);
        int red = hostile ? 255 : 190, green = hostile ? 100 : 225, blue = hostile ? 55 : 255;
        vertices.addVertex(matrix, -size, -size, 0).setColor(red, green, blue, a);
        vertices.addVertex(matrix, size, -size, 0).setColor(red, green, blue, a);
        vertices.addVertex(matrix, size, size, 0).setColor(red, green, blue, a);
        vertices.addVertex(matrix, -size, size, 0).setColor(red, green, blue, a);
    }

    @Override public ResourceLocation getTextureLocation(ObserverEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
