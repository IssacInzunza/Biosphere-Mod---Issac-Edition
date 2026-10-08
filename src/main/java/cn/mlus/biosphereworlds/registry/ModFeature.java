package cn.mlus.biosphereworlds.registry;

import cn.mlus.biosphereworlds.BiosphereWorlds;
import cn.mlus.biosphereworlds.feature.SphereFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFeature {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BiosphereWorlds.MODID);

    public static final DeferredHolder<Feature<?>,?> BIOSPHERE_FEATURE = FEATURES.register("sphere_feature", () -> new SphereFeature(SphereFeature.Config.CODEC));
    public static final ResourceKey<PlacedFeature> BIOSPHERE_FEATURE_KEY = ResourceKey.create(Registries.PLACED_FEATURE, BiosphereWorlds.prefix("sphere_feature"));


}
