package dev.ftb.mods.ftbquests.quest.task;

import dev.ftb.mods.ftbquests.quest.TeamData;

public interface TaskScreenResourceConsumer {
	ThroughputResourceType<?> getResourceType();

	long recordThroughput(TeamData data, ThroughputResourceType<?> type, long amount, boolean simulate);
}
