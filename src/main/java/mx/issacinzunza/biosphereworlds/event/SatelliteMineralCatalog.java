package mx.issacinzunza.biosphereworlds.event;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.worldgen.MineralCatalog;
import mx.issacinzunza.biosphereworlds.worldgen.SatelliteLayers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Catalogo de minerales de los satelites.
 *
 * <p>Se reconstruye una sola vez por carga de tags (arranque o recarga de
 * datapacks) y publica una lista inmutable. El worldgen corre en varios hilos y
 * solo lee esa referencia, asi que no hay estado compartido mutable.</p>
 */
@EventBusSubscriber
public final class SatelliteMineralCatalog {

    private static final TagKey<Block> ORES_IN_STONE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "ores_in_ground/stone"));

    private static final AtomicBoolean WARNED_FALLBACK = new AtomicBoolean();

    private static volatile List<SatelliteLayers.Mineral> candidates;

    private SatelliteMineralCatalog() {
    }

    /** Lista actual; si aun no hay snapshot devuelve la vanilla de la etapa 2. */
    public static List<SatelliteLayers.Mineral> candidates() {
        List<SatelliteLayers.Mineral> current = candidates;
        if (current != null) {
            return current;
        }
        if (WARNED_FALLBACK.compareAndSet(false, true)) {
            BiosphereWorlds.LOGGER.warn(
                    "satellite mineral catalog is not ready yet; using the vanilla fallback list");
        }
        return SatelliteLayers.VANILLA_MINERALS;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) {
            return;
        }
        try {
            rebuild(event);
        } catch (RuntimeException exception) {
            BiosphereWorlds.LOGGER.error(
                    "could not rebuild the satellite mineral catalog; keeping the previous one",
                    exception);
        }
    }

    private static void rebuild(TagsUpdatedEvent event) {
        Registry<Block> registry = event.getRegistryAccess()
                .registry(Registries.BLOCK)
                .orElse(BuiltInRegistries.BLOCK);

        Set<String> ignored = new LinkedHashSet<>();
        Set<String> excluded = new LinkedHashSet<>();
        for (String entry : SphereConfig.SATELLITES_EXCLUDED_ORES.get()) {
            resolve(entry, registry, excluded, ignored);
        }

        Set<String> included = new LinkedHashSet<>();
        for (String entry : SphereConfig.SATELLITES_INCLUDED_ORES.get()) {
            resolve(entry, registry, included, ignored);
        }

        Set<String> tagOres = new LinkedHashSet<>();
        addTag(ORES_IN_STONE, registry, tagOres, ignored);

        List<SatelliteLayers.Mineral> rebuilt = MineralCatalog.assemble(
                SatelliteLayers.VANILLA_MINERALS,
                tagOres,
                included,
                excluded,
                SphereConfig.SATELLITES_MOD_ORE_WEIGHT.get(),
                SphereConfig.SATELLITES_MOD_ORE_DENSITY_PERMILLE.get());
        if (rebuilt.isEmpty()) {
            BiosphereWorlds.LOGGER.warn(
                    "satellite mineral catalog would be empty; falling back to the vanilla list");
            rebuilt = SatelliteLayers.VANILLA_MINERALS;
        }

        candidates = rebuilt;
        BiosphereWorlds.LOGGER.info("satellite minerals rebuilt: candidates={} excluded={} ignored={}",
                describe(rebuilt), excluded, ignored);
    }

    private static void resolve(String rawEntry, Registry<Block> registry,
                                Set<String> destination, Set<String> ignored) {
        String entry = rawEntry == null ? "" : rawEntry.trim();
        if (entry.isEmpty()) {
            ignored.add("<blank>");
            return;
        }
        if (entry.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
            if (tagId == null) {
                ignored.add(entry + " (invalid tag id)");
                return;
            }
            addTag(TagKey.create(Registries.BLOCK, tagId), registry, destination, ignored);
            return;
        }

        ResourceLocation blockId = ResourceLocation.tryParse(entry);
        if (blockId == null) {
            ignored.add(entry + " (invalid block id)");
            return;
        }
        Block block = registry.get(blockId);
        if (block == null || block == Blocks.AIR || !isUsable(block)) {
            ignored.add(entry + " (missing or unusable block)");
            return;
        }
        destination.add(blockId.toString());
    }

    private static void addTag(TagKey<Block> tag, Registry<Block> registry,
                               Set<String> destination, Set<String> ignored) {
        Optional<net.minecraft.core.HolderSet.Named<Block>> holders = registry.getTag(tag);
        if (holders.isEmpty() || holders.get().size() == 0) {
            ignored.add("#" + tag.location() + " (missing or empty tag)");
            return;
        }
        for (Holder<Block> holder : holders.get()) {
            Block block = holder.value();
            ResourceLocation id = registry.getKey(block);
            if (block == Blocks.AIR || id == null || !isUsable(block)) {
                ignored.add(String.valueOf(id) + " (missing or unusable block)");
                continue;
            }
            destination.add(id.toString());
        }
    }

    private static boolean isUsable(Block block) {
        BlockState state = block.defaultBlockState();
        return !state.isAir()
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && state.canOcclude();
    }

    private static String describe(List<SatelliteLayers.Mineral> minerals) {
        StringBuilder message = new StringBuilder("[");
        for (SatelliteLayers.Mineral mineral : minerals) {
            if (message.length() > 1) {
                message.append(", ");
            }
            message.append(mineral.blockId())
                    .append("(weight=").append(mineral.weight())
                    .append(", density=").append(mineral.densityPermille()).append(')');
        }
        return message.append(']').toString();
    }
}
