package dev.ftb.mods.ftbquests.quest.task;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class EnergyThroughputType extends ThroughputResourceType<EnergyThroughputType.EnergyPayload> {
	public EnergyThroughputType() {
		super(FTBQuestsAPI.id("energy"), "FE/s", () -> Icon.getIcon("minecraft:item/redstone"));
	}

	@Override
	public EnergyPayload defaultPayload() {
		return EnergyPayload.INSTANCE;
	}

	@Override
	public void writeJson(Json5Object json, EnergyPayload payload, HolderLookup.Provider provider) {
	}

	@Override
	public EnergyPayload readJson(Json5Object json, HolderLookup.Provider provider) {
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
	public void fillConfigGroup(EditableConfigGroup config, ThroughputTask task, EnergyPayload payload) {
	}

	@Override
	public Icon<?> getIcon(ThroughputTask task, EnergyPayload payload) {
		return Icon.getIcon("minecraft:item/redstone");
	}

	@Override
	public boolean acceptsEnergy(ThroughputTask task, EnergyPayload payload) {
		return true;
	}

	public enum EnergyPayload {
		INSTANCE
	}
}
