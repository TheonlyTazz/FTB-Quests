package dev.ftb.mods.ftbquests.net;

import dev.ftb.mods.ftblibrary.platform.network.PacketContext;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.client.ClientThroughputTelemetry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ThroughputRateSyncMessage(UUID teamId, long taskId, long currentRate, long requiredRate, int windowTicks, int graceTicks, int belowRateTicks, Identifier resourceTypeId) implements CustomPacketPayload {
	public static final Type<ThroughputRateSyncMessage> TYPE = new Type<>(FTBQuestsAPI.id("throughput_rate_sync_message"));

	public static final StreamCodec<FriendlyByteBuf, ThroughputRateSyncMessage> STREAM_CODEC = StreamCodec.of(
			(buffer, message) -> {
				UUIDUtil.STREAM_CODEC.encode(buffer, message.teamId);
				buffer.writeVarLong(message.taskId);
				buffer.writeVarLong(message.currentRate);
				buffer.writeVarLong(message.requiredRate);
				buffer.writeVarInt(message.windowTicks);
				buffer.writeVarInt(message.graceTicks);
				buffer.writeVarInt(message.belowRateTicks);
				Identifier.STREAM_CODEC.encode(buffer, message.resourceTypeId);
			},
			buffer -> new ThroughputRateSyncMessage(
					UUIDUtil.STREAM_CODEC.decode(buffer),
					buffer.readVarLong(),
					buffer.readVarLong(),
					buffer.readVarLong(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					Identifier.STREAM_CODEC.decode(buffer)
			)
	);

	@Override
	public Type<ThroughputRateSyncMessage> type() {
		return TYPE;
	}

	public static void handle(ThroughputRateSyncMessage message, PacketContext ignoredContext) {
		ClientThroughputTelemetry.update(message.teamId, message.taskId, message.currentRate, message.requiredRate, message.windowTicks, message.graceTicks, message.belowRateTicks, message.resourceTypeId);
	}
}
