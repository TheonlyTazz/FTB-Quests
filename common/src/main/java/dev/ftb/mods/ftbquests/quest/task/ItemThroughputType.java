package dev.ftb.mods.ftbquests.quest.task;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType;
import dev.ftb.mods.ftbquests.item.MissingItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class ItemThroughputType extends ThroughputResourceType<ItemThroughputType.ItemPayload> {
	public ItemThroughputType() {
		super(FTBQuestsAPI.rl("item"), "items/s", () -> Icon.getIcon("minecraft:item/diamond"));
	}

	@Override
	public ItemPayload defaultPayload() {
		return new ItemPayload(ItemStack.EMPTY, ComponentMatchType.NONE);
	}

	@Override
	public void writeData(CompoundTag nbt, ItemPayload payload, HolderLookup.Provider provider) {
		nbt.put("item", Task.saveItemSingleLine(payload.stack.copyWithCount(1)));
		if (payload.matchComponents != ComponentMatchType.NONE) {
			nbt.putString("match_components", ComponentMatchType.NAME_MAP.getName(payload.matchComponents));
		}
	}

	@Override
	public ItemPayload readData(CompoundTag nbt, HolderLookup.Provider provider) {
		ItemStack stack = Task.readItemSingleLine(nbt.get("item"), provider);
		ComponentMatchType matchComponents = ComponentMatchType.NAME_MAP.get(nbt.getString("match_components"));
		return new ItemPayload(stack, matchComponents);
	}

	@Override
	public void writeNet(RegistryFriendlyByteBuf buffer, ItemPayload payload) {
		ItemStack.STREAM_CODEC.encode(buffer, payload.stack);
		ComponentMatchType.NAME_MAP.write(buffer, payload.matchComponents);
	}

	@Override
	public ItemPayload readNet(RegistryFriendlyByteBuf buffer) {
		ItemStack stack = ItemStack.STREAM_CODEC.decode(buffer);
		ComponentMatchType matchComponents = ComponentMatchType.NAME_MAP.read(buffer);
		return new ItemPayload(stack, matchComponents);
	}

	@Override
	public void fillConfigGroup(ConfigGroup config, ThroughputTask task, ItemPayload payload) {
		config.addItemStack("item", payload.stack, v -> task.setResourcePayload(this, new ItemPayload(v, payload.matchComponents)), ItemStack.EMPTY, false, false);
		config.addEnum("match_components", payload.matchComponents, v -> task.setResourcePayload(this, new ItemPayload(payload.stack, v)), ComponentMatchType.NAME_MAP);
	}

	@Override
	public Icon getIcon(ThroughputTask task, ItemPayload payload) {
		return payload.stack.isEmpty() ? getMenuIcon() : ItemIcon.getItemIcon(payload.stack);
	}

	@Override
	public Optional<PositionedIngredient> getIngredient(Widget widget, ItemPayload payload) {
		return PositionedIngredient.of(payload.stack, widget);
	}

	@Override
	public boolean acceptsItem(ThroughputTask task, ItemPayload payload, ItemStack stack) {
		return !(payload.stack.getItem() instanceof MissingItem)
				&& !stack.isEmpty()
				&& ItemMatchingSystem.INSTANCE.doesItemMatch(payload.stack, stack, payload.matchComponents, task.getQuestFile().holderLookup());
	}

	public record ItemPayload(ItemStack stack, ComponentMatchType matchComponents) {
	}
}
