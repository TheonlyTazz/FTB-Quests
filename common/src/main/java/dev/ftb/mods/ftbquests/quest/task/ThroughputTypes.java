package dev.ftb.mods.ftbquests.quest.task;

public final class ThroughputTypes {
	public static final ThroughputResourceType<?> ITEM = ThroughputResourceType.register(new ItemThroughputType());
	public static final ThroughputResourceType<?> FLUID = ThroughputResourceType.register(new FluidThroughputType());
	public static final ThroughputResourceType<?> ENERGY = ThroughputResourceType.register(new EnergyThroughputType());

	private ThroughputTypes() {
	}

	public static void init() {
	}
}
