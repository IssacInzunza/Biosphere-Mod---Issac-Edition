package mx.issacinzunza.biosphereworlds.mixin.accessor;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomState.class)
public interface SolarRandomStateAccess {
    @Accessor("noises")
    HolderGetter<NormalNoise.NoiseParameters> biosphereworlds$noiseParameters();
}
