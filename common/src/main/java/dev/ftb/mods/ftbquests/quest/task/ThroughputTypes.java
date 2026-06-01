package dev.ftb.mods.ftbquests.quest.task;

public final class ThroughputTypes {
	public static final ItemThroughputType ITEM = ThroughputResourceType.register(new ItemThroughputType());
	public static final FluidThroughputType FLUID = ThroughputResourceType.register(new FluidThroughputType());
	public static final EnergyThroughputType ENERGY = ThroughputResourceType.register(new EnergyThroughputType());

	private ThroughputTypes() {
	}

	public static void init() {
	}
}
