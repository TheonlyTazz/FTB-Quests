package dev.ftb.mods.ftbquests.quest.task;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class EnergyThroughputType extends ThroughputResourceType<EnergyThroughputType.EnergyPayload> {
	public EnergyThroughputType() {
		super(FTBQuestsAPI.rl("energy"), "FE/s", () -> Icon.getIcon("minecraft:item/redstone"));
	}

	@Override
	public EnergyPayload defaultPayload() {
		return EnergyPayload.INSTANCE;
	}

	@Override
	public void writeData(CompoundTag nbt, EnergyPayload payload, HolderLookup.Provider provider) {
	}

	@Override
	public EnergyPayload readData(CompoundTag nbt, HolderLookup.Provider provider) {
		return EnergyPayload.INSTANCE;
	}

	@Override
	public void writeNet(RegistryFriendlyByteBuf buffer, EnergyPayload payload) {
	}

	@Override
	public EnergyPayload readNet(RegistryFriendlyByteBuf buffer) {
		return EnergyPayload.INSTANCE;
	}

	@Override
	public void fillConfigGroup(ConfigGroup config, ThroughputTask task, EnergyPayload payload) {
	}

	@Override
	public Icon getIcon(ThroughputTask task, EnergyPayload payload) {
		return getMenuIcon();
	}

	@Override
	public boolean acceptsEnergy(ThroughputTask task, EnergyPayload payload) {
		return true;
	}

	public enum EnergyPayload {
		INSTANCE
	}
}
