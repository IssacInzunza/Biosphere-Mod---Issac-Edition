package mx.issacinzunza.biosphereworlds.worldgen;

public interface BiosphereBiomeSourceAccess extends ClassicWorldSettings.Access {
    boolean biosphereworlds$isBiosphereBiomeSource();

    void biosphereworlds$setBiosphereBiomeSource(boolean biosphere);

    mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource biosphereworlds$solarBiomes();

    void biosphereworlds$setSolarBiomes(mx.issacinzunza.biosphereworlds.worldgen.solar.SolarBiomeSource solar);
}
