package mx.issacinzunza.biosphereworlds.mixin;

import mx.issacinzunza.biosphereworlds.config.SphereConfig;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.registry.ModBiomeAccess;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereWorldgen;
import mx.issacinzunza.biosphereworlds.worldgen.BiosphereBiomeSourceAccess;
import mx.issacinzunza.biosphereworlds.worldgen.SphereLayout;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiNoiseBiomeSource.class, priority = 500)
public abstract class MixinMultiNoiseBiomeSource implements BiosphereBiomeSourceAccess {
    @Shadow public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint);

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("HEAD"), cancellable = true)
    private void getNoiseBiome(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir){
        if (!biosphereworlds$isBiosphereBiomeSource()) {
            return;
        }

        Holder<Biome> biomeHolder = this.getNoiseBiome(sampler.sample(x, y, z));

        int i = QuartPos.toBlock(x);
        int j = QuartPos.toBlock(y);
        int k = QuartPos.toBlock(z);
        boolean outsideSphere = !SphereLayout.current().isInside(i, j, k, -4);
        if (SphereConfig.DEBUG_BIOME_LOOKUPS.get() && outsideSphere) {
            BiosphereWorlds.LOGGER.debug("biome-route=MultiNoiseBiomeSource#getNoiseBiome sampler=({},{},{}) block=({},{},{}) before={} marked={}",
                x, y, z, i, j, k,
                biomeHolder.unwrapKey().map(key -> key.location().toString()).orElse("unregistered"),
                biosphereworlds$isBiosphereBiomeSource());
        }
        if (outsideSphere) {
            Holder<Biome> voidBiome = ModBiomeAccess.voidBiome();
            if (voidBiome != null && voidBiome.isBound())
                cir.setReturnValue(voidBiome);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private volatile boolean biosphereworlds$biosphereBiomeSource;

    @Override
    public boolean biosphereworlds$isBiosphereBiomeSource() {
        return biosphereworlds$biosphereBiomeSource;
    }

    @Override
    public void biosphereworlds$setBiosphereBiomeSource(boolean biosphere) {
        biosphereworlds$biosphereBiomeSource = biosphere;
    }
}
