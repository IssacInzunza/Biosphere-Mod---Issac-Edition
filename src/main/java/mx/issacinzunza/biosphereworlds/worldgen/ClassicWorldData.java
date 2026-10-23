package mx.issacinzunza.biosphereworlds.worldgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Guardado versionado de mundos nuevos; la ausencia en mundos antiguos significa legacy. */
public final class ClassicWorldData extends SavedData {
    public static final String ID = "biosphereworlds_classic";
    private static final int VERSION = 1;
    private static final Factory<ClassicWorldData> FACTORY = new Factory<>(
            () -> { throw new IllegalStateException("Classic recipe creation must be explicit"); },
            (tag, registries) -> new ClassicWorldData(tag));
    private final CompoundTag payload;

    private ClassicWorldData(CompoundTag payload) { this.payload = payload.copy(); }

    /** La decodificacion se hace fuera del lector vanilla, que captura excepciones y devuelve null. */
    public static ClassicWorldSettings.Bound load(DimensionDataStorage storage, Path file, boolean newWorld,
                                                  Supplier<ClassicWorldSettings> capture) {
        boolean missing = Files.notExists(file);
        var data = storage.get(FACTORY, ID);
        if (data == null) {
            if (!missing) throw new IllegalStateException("Cannot read classic world recipe: " + file);
            if (!newWorld) return null;
            var settings = capture.get();
            settings.validateBlocks();
            var tag = new CompoundTag();
            tag.putInt("schema_version", VERSION);
            tag.put("settings", ClassicWorldSettings.CODEC.encodeStart(NbtOps.INSTANCE, settings).getOrThrow());
            data = new ClassicWorldData(tag);
            data.setDirty();
            storage.set(ID, data);
        }
        if (!data.payload.contains("schema_version", 3) || data.payload.getInt("schema_version") != VERSION) {
            throw new IllegalArgumentException("Unsupported classic world recipe version in " + file);
        }
        try {
            var settings = ClassicWorldSettings.CODEC.parse(NbtOps.INSTANCE, data.payload.get("settings")).getOrThrow();
            settings.validateBlocks();
            return new ClassicWorldSettings.Bound(settings);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid classic world recipe: " + file, invalid);
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { return payload.copy(); }
}
