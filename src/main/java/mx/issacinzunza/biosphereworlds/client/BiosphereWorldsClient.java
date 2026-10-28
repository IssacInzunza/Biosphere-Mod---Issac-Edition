package mx.issacinzunza.biosphereworlds.client;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.EasterEggConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import mx.issacinzunza.biosphereworlds.event.SolarGuidePayload;
import mx.issacinzunza.biosphereworlds.event.SolarPlanetGuidePayload;
import mx.issacinzunza.biosphereworlds.config.SolarConfig;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPlanetGuide;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Client-only entrypoint: enables the "Config" button in the mod list using
 * NeoForge's built-in configuration screen (no Configured dependency).
 */
@Mod(value = BiosphereWorlds.MODID, dist = Dist.CLIENT)
public class BiosphereWorldsClient {
    private static final ResourceLocation GUIDE_TEXTURE = BiosphereWorlds.prefix("textures/environment/solar_guide.png");
    // Render-thread state, bound to the actual level instance, never shared with worldgen.
    private static SolarGuidePayload guide;
    private static ClientLevel guideLevel;
    private static long guideExpires;
    private static SolarPlanetGuidePayload planetGuides;
    private static ClientLevel planetGuideLevel;
    private static long planetGuidesExpire;

    public BiosphereWorldsClient(net.neoforged.bus.api.IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) ->
                new ConfigurationScreen(container, parent, (screen, type, config, title) -> {
                    if (config.getSpec() != EasterEggConfig.SPEC)
                        return new ConfigurationScreen.ConfigurationSectionScreen(screen, type, config, title);
                    // Open the existing category directly, preserving its saved key and
                    // NeoForge's native editing, undo, save and reload behavior.
                    UnmodifiableConfig values = EasterEggConfig.SPEC.getValues().get("Easter Egg");
                    UnmodifiableConfig specs = EasterEggConfig.SPEC.getSpec().get("Easter Egg");
                    var context = ConfigurationScreen.ConfigurationSectionScreen.Context.top(config.getModId(), screen, config, null);
                    return new ConfigurationScreen.ConfigurationSectionScreen(context, screen, specs.valueMap(),
                            "Easter Egg", values.entrySet(), title);
                }));
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(mx.issacinzunza.biosphereworlds.easteregg.ObserverEntity.TYPE.get(), ObserverRenderer::new));
        NeoForge.EVENT_BUS.addListener(BiosphereWorldsClient::renderGuide);
        NeoForge.EVENT_BUS.addListener(BiosphereWorldsClient::renderPlanetGuides);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            guide = null;
            guideLevel = null;
            planetGuides = null;
            planetGuideLevel = null;
        });
    }

    public static void receiveGuide(SolarGuidePayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().toString().equals(payload.dimension())) return;
        if (!mx.issacinzunza.biosphereworlds.config.SolarConfig.OPTIONS.SOLAR_GUIDE_ENABLED.get()) {
            guide = null;
            guideLevel = null;
            return;
        }
        guide = payload;
        guideLevel = level;
        guideExpires = level.getGameTime() + 40;
    }

    private static void renderGuide(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        var minecraft = Minecraft.getInstance();
        if (guide == null || minecraft.level != guideLevel || minecraft.player == null
                || guideLevel.getGameTime() > guideExpires) return;
        if (!mx.issacinzunza.biosphereworlds.config.SolarConfig.OPTIONS.SOLAR_GUIDE_ENABLED.get()) {
            guide = null;
            guideLevel = null;
            return;
        }
        float opacity = SolarLayout.guideOpacity(minecraft.player.getX(), minecraft.player.getZ(),
                guide.x(), guide.z(), guide.radius());
        if (opacity <= 0) return;
        var camera = event.getCamera();
        double dx = guide.x() - camera.getPosition().x;
        double dz = guide.z() - camera.getPosition().z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 0.001) return;
        // Fixed elevation (~22 degrees): a readable sky bearing, independent of center Y.
        var matrix = new Matrix4f(event.getModelViewMatrix())
                .translate((float) (dx / horizontal * 60), 24, (float) (dz / horizontal * 60))
                .rotate(camera.rotation());
        float time = guideLevel.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float pulse = 0.94f + 0.06f * (float) Math.sin(time * Math.PI / 50);
        var previousShader = RenderSystem.getShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GUIDE_TEXTURE);
        try {
            drawStar(matrix, 2.4f * pulse, opacity * 0.18f, 170, 80, 255);
            drawStar(matrix, 1.4f, opacity * pulse, 255, 255, 255);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
        }
    }

    public static void receivePlanetGuides(SolarPlanetGuidePayload payload) {
        if (!SolarConfig.OPTIONS.SOLAR_PLANET_GUIDES_ENABLED.get()) {
            planetGuides = null;
            planetGuideLevel = null;
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().toString().equals(payload.system().dimension())) return;
        planetGuides = payload;
        planetGuideLevel = level;
        planetGuidesExpire = level.getGameTime() + 40;
    }

    private static void renderPlanetGuides(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!SolarConfig.OPTIONS.SOLAR_PLANET_GUIDES_ENABLED.get()) {
            planetGuides = null;
            planetGuideLevel = null;
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (planetGuides == null || minecraft.level != planetGuideLevel || minecraft.player == null
                || planetGuideLevel.getGameTime() > planetGuidesExpire) return;
        var system = planetGuides.system();
        float envelope = SolarLayout.guideOpacity(minecraft.player.getX(), minecraft.player.getZ(),
                system.x(), system.z(), system.radius());
        if (envelope <= 0) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long dayTime = planetGuideLevel.getDayTime();
        var camera = event.getCamera();
        // Match the existing guide's render state, but keep its drawing path untouched.
        boolean drawing = false;
        var previousShader = RenderSystem.getShader();
        try {
            for (var planet : planetGuides.planets()) {
                float opacity = envelope * SolarPlanetGuide.opacity(dayTime, partial, planet.slot());
                if (opacity <= 0) continue;
                double dx = planet.x() - camera.getPosition().x;
                double dz = planet.z() - camera.getPosition().z;
                double horizontal = Math.hypot(dx, dz);
                if (horizontal < 0.001) continue;
                var matrix = new Matrix4f(event.getModelViewMatrix())
                        .translate((float) (dx / horizontal * 60), 30, (float) (dz / horizontal * 60))
                        .rotate(camera.rotation());
                if (!drawing) {
                    drawing = true;
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                    RenderSystem.setShaderTexture(0, GUIDE_TEXTURE);
                }
                int color = planet.color();
                drawStar(matrix, 1.5f, opacity * 0.15f, (color >> 16) & 255, (color >> 8) & 255, color & 255);
                drawStar(matrix, 0.8f, opacity, (color >> 16) & 255, (color >> 8) & 255, color & 255);
            }
        } finally {
            if (drawing) {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
                RenderSystem.setShader(() -> previousShader);
            }
        }
    }

    private static void drawStar(Matrix4f matrix, float size, float alpha, int red, int green, int blue) {
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int a = Math.clamp((int) (alpha * 255), 0, 255);
        buffer.addVertex(matrix, -size, -size, 0).setUv(0, 1).setColor(red, green, blue, a);
        buffer.addVertex(matrix, size, -size, 0).setUv(1, 1).setColor(red, green, blue, a);
        buffer.addVertex(matrix, size, size, 0).setUv(1, 0).setColor(red, green, blue, a);
        buffer.addVertex(matrix, -size, size, 0).setUv(0, 0).setColor(red, green, blue, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
