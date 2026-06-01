package dev.ftb.mods.ftbquests.net;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.client.ClientThroughputTelemetry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ThroughputRateSyncMessage(UUID teamId, long taskId, long currentRate, long requiredRate, int windowTicks, int graceTicks, int belowRateTicks, ResourceLocation resourceTypeId) implements CustomPacketPayload {
	public static final Type<ThroughputRateSyncMessage> TYPE = new Type<>(FTBQuestsAPI.rl("throughput_rate_sync"));

	public static final StreamCodec<FriendlyByteBuf, ThroughputRateSyncMessage> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ThroughputRateSyncMessage::teamId,
			ByteBufCodecs.VAR_LONG, ThroughputRateSyncMessage::taskId,
			ByteBufCodecs.VAR_LONG, ThroughputRateSyncMessage::currentRate,
			ByteBufCodecs.VAR_LONG, ThroughputRateSyncMessage::requiredRate,
			ByteBufCodecs.VAR_INT, ThroughputRateSyncMessage::windowTicks,
			ByteBufCodecs.VAR_INT, ThroughputRateSyncMessage::graceTicks,
			ByteBufCodecs.VAR_INT, ThroughputRateSyncMessage::belowRateTicks,
			ResourceLocation.STREAM_CODEC, ThroughputRateSyncMessage::resourceTypeId,
			ThroughputRateSyncMessage::new
	);

	@Override
	public Type<ThroughputRateSyncMessage> type() {
		return TYPE;
	}

	public static void handle(ThroughputRateSyncMessage message, NetworkManager.PacketContext ignoredContext) {
		ClientThroughputTelemetry.update(message.teamId, message.taskId, message.currentRate, message.requiredRate, message.windowTicks, message.graceTicks, message.belowRateTicks, message.resourceTypeId);
	}
}
