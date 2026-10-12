package mx.issacinzunza.biosphereworlds.client;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entrypoint: enables the "Config" button in the mod list using
 * NeoForge's built-in configuration screen (no Configured dependency).
 */
@Mod(value = BiosphereWorlds.MODID, dist = Dist.CLIENT)
public class BiosphereWorldsClient {
    public BiosphereWorldsClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
