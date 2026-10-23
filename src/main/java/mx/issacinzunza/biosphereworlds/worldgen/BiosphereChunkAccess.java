package mx.issacinzunza.biosphereworlds.worldgen;

public interface BiosphereChunkAccess extends ClassicWorldSettings.Access {
    boolean biosphereworlds$isBiosphere();

    void biosphereworlds$setBiosphere(boolean biosphere);
}
