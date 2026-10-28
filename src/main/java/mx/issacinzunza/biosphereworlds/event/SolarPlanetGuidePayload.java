package mx.issacinzunza.biosphereworlds.event;

import io.netty.buffer.ByteBuf;
import java.util.List;
import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import mx.issacinzunza.biosphereworlds.worldgen.solar.SolarPlanetGuide.Marker;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Separate optional channel: the original central-guide payload remains unchanged. */
public record SolarPlanetGuidePayload(SolarGuidePayload system, List<Marker> planets) implements CustomPacketPayload {
    public static final Type<SolarPlanetGuidePayload> TYPE = new Type<>(BiosphereWorlds.prefix("solar_planet_guides"));
    private static final StreamCodec<ByteBuf, Marker> MARKER_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Marker::x,
            ByteBufCodecs.DOUBLE, Marker::z,
            ByteBufCodecs.VAR_INT, Marker::color,
            ByteBufCodecs.VAR_INT, Marker::slot, Marker::new);
    public static final StreamCodec<ByteBuf, SolarPlanetGuidePayload> STREAM_CODEC = StreamCodec.composite(
            SolarGuidePayload.STREAM_CODEC, SolarPlanetGuidePayload::system,
            MARKER_CODEC.apply(ByteBufCodecs.list(16)), SolarPlanetGuidePayload::planets, SolarPlanetGuidePayload::new);

    public SolarPlanetGuidePayload {
        planets = List.copyOf(planets);
    }

    @Override public Type<SolarPlanetGuidePayload> type() { return TYPE; }
}
