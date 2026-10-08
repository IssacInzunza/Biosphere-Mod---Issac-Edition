package cn.mlus.neobiosphere.mixin;

import cn.mlus.neobiosphere.carver.SphereCarver;
import cn.mlus.neobiosphere.config.SphereConfig;
import cn.mlus.neobiosphere.Neobiosphere;
import cn.mlus.neobiosphere.registry.ModBiomeAccess;
import cn.mlus.neobiosphere.worldgen.BiosphereWorldgen;
import cn.mlus.neobiosphere.worldgen.BiosphereBiomeSourceAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MixinMultiNoiseBiomeSource implements BiosphereBiomeSourceAccess {
    @Shadow public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint);

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("HEAD"), cancellable = true)
    private void getNoiseBiome(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir){
        if (!neobiosphere$isBiosphereBiomeSource()) {
            return;
        }

        Holder<Biome> biomeHolder = this.getNoiseBiome(sampler.sample(x, y, z));

        int i = QuartPos.toBlock(x);
        int j = QuartPos.toBlock(y);
        int k = QuartPos.toBlock(z);
        boolean outsideSphere = !SphereCarver.isInsideAnySphere(i, j, k, -4);
        if (SphereConfig.DEBUG_BIOME_LOOKUPS.get() && outsideSphere) {
            Neobiosphere.LOGGER.debug("biome-route=MultiNoiseBiomeSource#getNoiseBiome sampler=({},{},{}) block=({},{},{}) before={} marked={}",
                x, y, z, i, j, k,
                biomeHolder.unwrapKey().map(key -> key.location().toString()).orElse("unregistered"),
                neobiosphere$isBiosphereBiomeSource());
        }
        if (outsideSphere) {
            Holder<Biome> voidBiome = ModBiomeAccess.LOOKUP.getOrThrow(Biomes.THE_VOID);
            if(voidBiome.isBound())
                cir.setReturnValue(voidBiome);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private volatile boolean neobiosphere$biosphereBiomeSource;

    @Override
    public boolean neobiosphere$isBiosphereBiomeSource() {
        return neobiosphere$biosphereBiomeSource;
    }

    @Override
    public void neobiosphere$setBiosphereBiomeSource(boolean biosphere) {
        neobiosphere$biosphereBiomeSource = biosphere;
    }
}
