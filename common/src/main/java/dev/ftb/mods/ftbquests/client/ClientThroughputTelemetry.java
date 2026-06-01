package dev.ftb.mods.ftbquests.client;

import dev.ftb.mods.ftbquests.quest.task.ThroughputResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ClientThroughputTelemetry {
	private static final long EXPIRE_TICKS = 60L;
	private static final Map<Key, Entry> CACHE = new HashMap<>();

	public static void update(UUID teamId, long taskId, long currentRate, long requiredRate, int windowTicks, int graceTicks, int belowRateTicks, ResourceLocation resourceTypeId) {
		CACHE.put(new Key(teamId, taskId), new Entry(currentRate, requiredRate, windowTicks, graceTicks, belowRateTicks, resourceTypeId, now()));
	}

	public static void tick() {
		if (CACHE.isEmpty()) return;
		long now = now();
		CACHE.values().removeIf(entry -> now - entry.receivedTick > EXPIRE_TICKS);
	}

	public static Optional<Entry> get(UUID teamId, long taskId) {
		Entry entry = CACHE.get(new Key(teamId, taskId));
		if (entry == null) {
			return Optional.empty();
		}
		if (now() - entry.receivedTick > EXPIRE_TICKS) {
			CACHE.remove(new Key(teamId, taskId));
			return Optional.empty();
		}
		return Optional.of(entry);
	}

	private static long now() {
		return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
	}

	private record Key(UUID teamId, long taskId) {
	}

	public record Entry(long currentRate, long requiredRate, int windowTicks, int graceTicks, int belowRateTicks, ResourceLocation resourceTypeId, long receivedTick) {
		public String format() {
			ThroughputResourceType<?> type = ThroughputResourceType.get(resourceTypeId);
			return type.formatRate(currentRate) + " / " + type.formatRate(requiredRate);
		}
	}
}
