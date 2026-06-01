package dev.ftb.mods.ftbquests.quest.task;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.NameMap;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class ThroughputResourceType<TPayload> {
	private static final String LANG_PREFIX = "ftbquests.task.ftbquests.throughput.resource_type";
	private static final Map<ResourceLocation, ThroughputResourceType<?>> TYPES = new LinkedHashMap<>();

	private final ResourceLocation id;
	private final String unit;
	private final Supplier<Icon> menuIconSupplier;

	public ThroughputResourceType(ResourceLocation id, String unit) {
		this(id, unit, () -> Icon.getIcon("minecraft:item/hopper"));
	}

	public ThroughputResourceType(ResourceLocation id, String unit, Supplier<Icon> menuIconSupplier) {
		this.id = id;
		this.unit = unit;
		this.menuIconSupplier = menuIconSupplier;
	}

	public static void init() {
		ThroughputTypes.init();
	}

	public static <TPayload, TType extends ThroughputResourceType<TPayload>> TType register(TType type) {
		Objects.requireNonNull(type, "type");
		if (TYPES.putIfAbsent(type.id(), type) != null) {
			throw new IllegalArgumentException("Duplicate throughput resource type: " + type.id());
		}
		return type;
	}

	public static ThroughputResourceType<?> get(ResourceLocation id) {
		init();
		return TYPES.getOrDefault(id, ThroughputTypes.ITEM);
	}

	public static Collection<ThroughputResourceType<?>> values() {
		init();
		return TYPES.values();
	}

	public static NameMap<ThroughputResourceType<?>> nameMap() {
		init();
		return NameMap.<ThroughputResourceType<?>>of(ThroughputTypes.ITEM, new ArrayList<>(TYPES.values()))
				.id(ThroughputResourceType::getTypeForSerialization)
				.nameKey(ThroughputResourceType::getTranslationKey)
				.create();
	}

	public ResourceLocation id() {
		return id;
	}

	public String unit() {
		return unit;
	}

	public String getTypeForSerialization() {
		return id.getNamespace().equals(FTBQuestsAPI.MOD_ID) ? id.getPath() : id.toString();
	}

	public String getTranslationKey() {
		return id.getNamespace().equals(FTBQuestsAPI.MOD_ID)
				? LANG_PREFIX + "." + id.getPath()
				: LANG_PREFIX + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
	}

	public Component getDisplayName() {
		return Component.translatable(getTranslationKey());
	}

	public Icon getMenuIcon() {
		return menuIconSupplier.get();
	}

	public abstract TPayload defaultPayload();

	public abstract void writeData(CompoundTag nbt, TPayload payload, HolderLookup.Provider provider);

	public abstract TPayload readData(CompoundTag nbt, HolderLookup.Provider provider);

	public abstract void writeNet(RegistryFriendlyByteBuf buffer, TPayload payload);

	public abstract TPayload readNet(RegistryFriendlyByteBuf buffer);

	public abstract void fillConfigGroup(ConfigGroup config, ThroughputTask task, TPayload payload);

	public abstract Icon getIcon(ThroughputTask task, TPayload payload);

	public Optional<PositionedIngredient> getIngredient(Widget widget, TPayload payload) {
		return Optional.empty();
	}

	public boolean acceptsItem(ThroughputTask task, TPayload payload, ItemStack stack) {
		return false;
	}

	public boolean acceptsFluid(ThroughputTask task, TPayload payload, Fluid fluid) {
		return false;
	}

	public boolean acceptsEnergy(ThroughputTask task, TPayload payload) {
		return false;
	}

	public String formatRate(long rate) {
		return rate + " " + unit;
	}

	@Override
	public String toString() {
		return id.toString();
	}
}
