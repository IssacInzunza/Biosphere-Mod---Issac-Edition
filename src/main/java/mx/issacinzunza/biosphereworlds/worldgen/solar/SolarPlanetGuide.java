package mx.issacinzunza.biosphereworlds.worldgen.solar;

import java.util.ArrayList;
import java.util.List;

/** Pure navigation data and calendar; never changes the layout or reads global config. */
public final class SolarPlanetGuide {
    private SolarPlanetGuide() { }

    public record Marker(double x, double z, int color, int slot) { }

    public static List<Marker> markers(SolarLayout.System system, double x, double y, double z) {
        var markers = new ArrayList<Marker>();
        int index = 0;
        for (var body : system.bodies()) {
            if (body.central()) continue;
            int slot = index++ % 4;
            // Do not point at the planet the player is standing inside.
            if (body.contains(x, y, z, 0)) continue;
            markers.add(new Marker(body.x(), body.z(), color(body.climate()), slot));
        }
        return List.copyOf(markers);
    }

    public static int color(SolarLayout.Climate climate) {
        return switch (climate) {
            case HOT -> 0xFFB574;
            case TEMPERATE -> 0xA5D6A7;
            case COLD -> 0xB3DFFF;
        };
    }

    /** Four alternating half-night slots, with 200-tick smooth fades at both ends. */
    public static float opacity(long dayTime, float partialTick, int slot) {
        if (slot < 0 || slot > 3) return 0;
        double time = Math.floorMod(dayTime, 48000L) + partialTick;
        int start = 13000 + (slot / 2) * 24000 + (slot % 2) * 5000;
        double fadeIn = Math.clamp((time - start) / 200.0, 0, 1);
        double fadeOut = Math.clamp((start + 5000 - time) / 200.0, 0, 1);
        return (float) (fadeIn * fadeIn * (3 - 2 * fadeIn) * fadeOut * fadeOut * (3 - 2 * fadeOut));
    }
}
