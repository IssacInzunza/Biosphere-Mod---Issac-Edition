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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiNoiseBiomeSource.class, priority = 500)
public abstract class MixinMultiNoiseBiomeSource implements BiosphereBiomeSourceAccess {
    @org.spongepowered.asm.mixin.Unique
    private volatile mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound biosphereworlds$classicWorld;

    @Override public mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound biosphereworlds$classicWorld() {
        return biosphereworlds$classicWorld;
    }
    @Override public void biosphereworlds$setClassicWorld(mx.issacinzunza.biosphereworlds.worldgen.ClassicWorldSettings.Bound world) {
        if (biosphereworlds$classicWorld != null && biosphereworlds$classicWorld != world) {
            throw new IllegalStateException("Classic biome source shared between different worlds");
        }
        biosphereworlds$classicWorld = world;
    }
    @Shadow public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint);

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("HEAD"), cancellable = true)
    private void getNoiseBiome(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir){
        var solar = biosphereworlds$solarBiomes;
        if (solar != null) {
            Holder<Biome> forced = solar.forcedBiome(x, y, z);
            if (forced != null) cir.setReturnValue(forced);
            return;
        }
        if (!biosphereworlds$isBiosphereBiomeSource()) {
            return;
        }

        int i = QuartPos.toBlock(x);
        int j = QuartPos.toBlock(y);
        int k = QuartPos.toBlock(z);
        boolean outsideSphere = !BiosphereWorldgen.classicLayout(this).isInside(i, j, k, -4);
        if (SphereConfig.DEBUG_BIOME_LOOKUPS.get() && outsideSphere) {
            Holder<Biome> biomeHolder = this.getNoiseBiome(sampler.sample(x, y, z));
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

    // Run before TerraBlender's priority-1000 HEAD handler, retaining positional region selection.
    @ModifyVariable(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"), argsOnly = true)
    private Climate.Sampler solarSampler(Climate.Sampler sampler, int x, int y, int z, Climate.Sampler original) {
        var solar = biosphereworlds$solarBiomes;
        return solar == null ? sampler : solar.climateSampler(x, y, z, sampler);
    }

    @org.spongepowered.asm.mixin.Unique
    private volatile mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource biosphereworlds$solarBiomes;

    @Override
    public mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource biosphereworlds$solarBiomes() {
        return biosphereworlds$solarBiomes;
    }

    @Override
    public void biosphereworlds$setSolarBiomes(mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource solar) {
        biosphereworlds$solarBiomes = solar;
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
