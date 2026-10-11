package cn.mlus.biosphereworlds.util;

import cn.mlus.biosphereworlds.config.SphereConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class SphereUtil {
    public static boolean isSphereBlock(BlockState state){
        for (String blockId : SphereConfig.SPHERE_BLOCK.get()) {
            if (state.is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId)))) {
                return true;
            }
        }
        return false;
    }
}
