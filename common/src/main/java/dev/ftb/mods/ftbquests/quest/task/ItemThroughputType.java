package dev.ftb.mods.ftbquests.quest.task;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.icon.AnimatedIcon;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.json5.Json5Ops;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType;
import dev.ftb.mods.ftbquests.item.MissingItem;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ItemThroughputType extends ThroughputResourceType<ItemThroughputType.ItemPayload> {
	public ItemThroughputType() {
		super(FTBQuestsAPI.id("item"), "items/s", () -> Icon.getIcon("minecraft:item/apple"));
	}

	@Override
	public ItemPayload defaultPayload() {
		return new ItemPayload(new ItemStack(Items.APPLE, 1), ComponentMatchType.NONE);
	}

	@Override
	public void writeJson(Json5Object json, ItemPayload payload, HolderLookup.Provider provider) {
		json.add("item", ItemStack.CODEC.encodeStart(provider.createSerializationContext(Json5Ops.INSTANCE), payload.stack.copyWithCount(1)).getOrThrow());
		if (payload.matchComponents != ComponentMatchType.NONE) {
			json.addProperty("match_components", ComponentMatchType.NAME_MAP.getName(payload.matchComponents));
		}
	}

	@Override
	public ItemPayload readJson(Json5Object json, HolderLookup.Provider provider) {
		ItemStack stack = Json5Util.getJson5Object(json, "item")
				.map(o -> QuestObjectBase.itemOrMissingFromJson(o, provider))
				.orElseGet(() -> new ItemStack(Items.APPLE, 1));
		if (stack.isEmpty()) {
			stack = new ItemStack(Items.APPLE, 1);
		}
		ComponentMatchType matchComponents = Json5Util.getString(json, "match_components")
				.map(ComponentMatchType.NAME_MAP::get)
				.orElse(ComponentMatchType.NONE);
		return new ItemPayload(stack.copyWithCount(1), matchComponents);
	}

	@Override
	public void writeNet(RegistryFriendlyByteBuf buffer, ItemPayload payload) {
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.stack);
		ComponentMatchType.NAME_MAP.write(buffer, payload.matchComponents);
	}

	@Override
	public ItemPayload readNet(RegistryFriendlyByteBuf buffer) {
		ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
		if (stack.isEmpty()) {
			stack = new ItemStack(Items.APPLE, 1);
		}
		return new ItemPayload(stack.copyWithCount(1), ComponentMatchType.NAME_MAP.read(buffer));
	}

	@Override
	public void fillConfigGroup(EditableConfigGroup config, ThroughputTask task, ItemPayload payload) {
		config.addItemStack("item", payload.stack, v -> task.setResourcePayload(this, new ItemPayload(v.isEmpty() ? new ItemStack(Items.APPLE, 1) : v.copyWithCount(1), payload.matchComponents)), new ItemStack(Items.APPLE, 1), true, false)
				.setNameKey("ftbquests.task.ftbquests.item");
		config.addEnum("match_components", payload.matchComponents, v -> task.setResourcePayload(this, new ItemPayload(payload.stack, v)), ComponentMatchType.NAME_MAP)
				.setNameKey("ftbquests.task.ftbquests.item.match_components");
	}

	@Override
	public Icon<?> getIcon(ThroughputTask task, ItemPayload payload) {
		List<Icon<?>> icons = new ArrayList<>();
		for (ItemStack stack : ItemMatchingSystem.INSTANCE.getAllMatchingStacks(payload.stack, task.getQuestFile().holderLookup())) {
			Icon<?> icon = ItemIcon.ofItemStack(stack.copyWithCount(1));
			if (!icon.isEmpty()) {
				icons.add(icon);
			}
		}
		return icons.isEmpty() ? ItemIcon.ofItem(ModItems.MISSING_ITEM.get()) : AnimatedIcon.fromList(icons, false);
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
