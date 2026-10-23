package mx.issacinzunza.biosphereworlds.event;

import mx.issacinzunza.biosphereworlds.BiosphereWorlds;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import io.netty.buffer.ByteBuf;

/** Only the current system's public navigation data; no seed or client-side layout copy. */
public record SolarGuidePayload(String dimension, double x, double z, int radius) implements CustomPacketPayload {
    public static final Type<SolarGuidePayload> TYPE = new Type<>(BiosphereWorlds.prefix("solar_guide"));
    public static final StreamCodec<ByteBuf, SolarGuidePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SolarGuidePayload::dimension,
            ByteBufCodecs.DOUBLE, SolarGuidePayload::x,
            ByteBufCodecs.DOUBLE, SolarGuidePayload::z,
            ByteBufCodecs.VAR_INT, SolarGuidePayload::radius, SolarGuidePayload::new);

    @Override public Type<SolarGuidePayload> type() { return TYPE; }
}
