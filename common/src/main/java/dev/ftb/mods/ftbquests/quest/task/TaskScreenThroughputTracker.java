package dev.ftb.mods.ftbquests.quest.task;

import dev.ftb.mods.ftblibrary.util.NetworkHelper;
import dev.ftb.mods.ftbquests.net.ThroughputRateSyncMessage;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TaskScreenThroughputTracker {
	private static final int EVALUATE_INTERVAL = 20;
	private static final int SYNC_INTERVAL = 10;
	private static final int INACTIVE_TIMEOUT = 1200;
	private static final Map<Key, Entry> ENTRIES = new HashMap<>();

	public static void record(UUID teamId, ThroughputTask task, long amount) {
		if (amount <= 0L || ServerQuestFile.INSTANCE == null) {
			return;
		}
		long tick = now(ServerQuestFile.INSTANCE.server);
		Entry entry = ENTRIES.computeIfAbsent(new Key(teamId, task.id), _ -> new Entry(tick));
		entry.samples.addTo(tick, amount);
		entry.windowSum += amount;
		entry.lastActivityTick = tick;
	}

	public static void tick(MinecraftServer server) {
		if (ENTRIES.isEmpty() || ServerQuestFile.INSTANCE == null) {
			return;
		}

		long tick = now(server);
		Iterator<Map.Entry<Key, Entry>> iterator = ENTRIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Key, Entry> mapEntry = iterator.next();
			Key key = mapEntry.getKey();
			Entry entry = mapEntry.getValue();

			TeamData data = ServerQuestFile.INSTANCE.getNullableTeamData(key.teamId);
			if (data == null || !(ServerQuestFile.INSTANCE.getTask(key.taskId) instanceof ThroughputTask task) || data.isCompleted(task)) {
				iterator.remove();
				continue;
			}

			prune(entry, tick, task.getWindow());
			entry.currentRate = entry.windowSum * 20L / task.getWindow();

			if (tick - entry.lastEvalTick >= EVALUATE_INTERVAL) {
				evaluate(data, task, entry, tick);
			}

			Collection<ServerPlayer> members = data.getOnlineMembers();
			if (!members.isEmpty() && tick - entry.lastSyncTick >= SYNC_INTERVAL) {
				entry.lastSyncTick = tick;
				ThroughputRateSyncMessage msg = new ThroughputRateSyncMessage(
						key.teamId,
						key.taskId,
						entry.currentRate,
						task.getRequiredRate(),
						task.getWindow(),
						task.getGraceTicks(),
						entry.belowRateTicks,
						task.getResourceTypeId()
				);
				members.forEach(player -> NetworkHelper.sendTo(player, msg));
			}

			if (tick - entry.lastActivityTick > INACTIVE_TIMEOUT && entry.windowSum <= 0L) {
				iterator.remove();
			}
		}
	}

	private static void evaluate(TeamData data, ThroughputTask task, Entry entry, long tick) {
		long elapsed = tick - entry.lastEvalTick;
		entry.lastEvalTick = tick;

		if (!data.canStartTasks(task.getQuest()) || !task.checkTaskSequence(data)) {
			return;
		}

		if (entry.currentRate >= task.getRequiredRate()) {
			entry.belowRateTicks = 0;
			data.setProgress(task, data.getProgress(task) + elapsed);
		} else {
			entry.belowRateTicks += (int) elapsed;
			if (entry.belowRateTicks > task.getGraceTicks()) {
				if (task.getFailureMode() == ThroughputTask.FailureMode.REGRESS) {
					data.setProgress(task, data.getProgress(task) - task.getRegressionRate());
				} else {
					data.setProgress(task, 0L);
				}
			}
		}
	}

	private static void prune(Entry entry, long tick, int window) {
		var iterator = entry.samples.long2LongEntrySet().iterator();
		while (iterator.hasNext()) {
			var sample = iterator.next();
			if (tick - sample.getLongKey() >= window) {
				entry.windowSum -= sample.getLongValue();
				iterator.remove();
			} else {
				break;
			}
		}
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private record Key(UUID teamId, long taskId) {
	}

	private static class Entry {
		private final Long2LongLinkedOpenHashMap samples = new Long2LongLinkedOpenHashMap();
		private long windowSum;
		private long currentRate;
		private int belowRateTicks;
		private long lastActivityTick;
		private long lastEvalTick;
		private long lastSyncTick;

		private Entry(long tick) {
			lastActivityTick = tick;
			lastEvalTick = tick;
			lastSyncTick = tick;
		}
	}
}
