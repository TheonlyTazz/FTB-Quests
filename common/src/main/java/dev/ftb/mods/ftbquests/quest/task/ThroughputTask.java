package dev.ftb.mods.ftbquests.quest.task;

import dev.architectury.fluid.FluidStack;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.NameMap;
import dev.ftb.mods.ftblibrary.util.StringUtils;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.client.ClientThroughputTelemetry;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.Optional;

public class ThroughputTask extends Task implements TaskScreenResourceConsumer {
	private ThroughputResourceType<?> resourceType = ThroughputTypes.ITEM;
	private Object resourcePayload = ThroughputTypes.ITEM.defaultPayload();
	private long requiredRate = 8L;
	private long duration = 1200L;
	private int window = 20;
	private int graceTicks = 20;
	private FailureMode failureMode = FailureMode.RESET;
	private long regressionRate = 20L;

	public ThroughputTask(long id, Quest quest) {
		super(id, quest);
		resourcePayload = resourceType.defaultPayload();
	}

	@Override
	public TaskType getType() {
		return TaskTypes.THROUGHPUT;
	}

	@Override
	public long getMaxProgress() {
		return duration;
	}

	@Override
	public String formatMaxProgress() {
		return formatTicks(duration);
	}

	@Override
	public String formatProgress(TeamData teamData, long progress) {
		return formatTicks(progress);
	}

	@Override
	public boolean consumesResources() {
		return true;
	}

	@Override
	public boolean canInsertItem() {
		return resourceType == ThroughputTypes.ITEM;
	}

	@Override
	public MutableComponent getAltTitle() {
		return Component.literal(formatRequiredRate()).append(" for " + formatTicks(duration));
	}

	@Override
	public Icon getAltIcon() {
		return getIconUnchecked(resourceType);
	}

	@Override
	public Optional<PositionedIngredient> getIngredient(Widget widget) {
		return getIngredientUnchecked(resourceType, widget).or(() -> super.getIngredient(widget));
	}

	@Override
	public ThroughputResourceType<?> getResourceType() {
		return resourceType;
	}

	public ResourceLocation getResourceTypeId() {
		return resourceType.id();
	}

	public ItemStack getItemStack() {
		return ((ItemThroughputType.ItemPayload) getPayload(ThroughputTypes.ITEM)).stack();
	}

	public FluidStack getFluidStack() {
		return ((FluidThroughputType.FluidPayload) getPayload(ThroughputTypes.FLUID)).stack();
	}

	public Fluid getFluid() {
		return getFluidStack().getFluid();
	}

	public DataComponentPatch getFluidDataComponentPatch() {
		return getFluidStack().getComponents().asPatch();
	}

	public long getRequiredRate() {
		return requiredRate;
	}

	public int getWindow() {
		return Math.max(1, window);
	}

	public int getGraceTicks() {
		return Math.max(0, graceTicks);
	}

	public FailureMode getFailureMode() {
		return failureMode;
	}

	public long getRegressionRate() {
		return Math.max(1L, regressionRate);
	}

	public String formatRequiredRate() {
		return resourceType.formatRate(requiredRate);
	}

	public String formatRate(long rate) {
		return resourceType.formatRate(rate);
	}

	public ThroughputTask configureDefaultResourceType(ThroughputResourceType<?> type) {
		resourceType = type;
		resourcePayload = defaultPayloadUnchecked(type);
		return this;
	}

	@SuppressWarnings("unchecked")
	public <T> T getPayload(ThroughputResourceType<T> type) {
		return resourceType == type ? (T) resourcePayload : type.defaultPayload();
	}

	public <T> void setResourcePayload(ThroughputResourceType<T> type, T payload) {
		if (resourceType == type) {
			resourcePayload = payload;
		}
	}

	public boolean acceptsItem(ItemStack stack) {
		return acceptsItemUnchecked(resourceType, stack);
	}

	public boolean acceptsFluid(Fluid fluid) {
		return acceptsFluidUnchecked(resourceType, fluid);
	}

	public boolean acceptsEnergy() {
		return acceptsEnergyUnchecked(resourceType);
	}

	@Override
	public long recordThroughput(TeamData data, ThroughputResourceType<?> type, long amount, boolean simulate) {
		if (amount <= 0L || !canAccept(data) || getResourceType() != type) {
			return 0L;
		}
		if (!simulate) {
			TaskScreenThroughputTracker.record(data.getTeamId(), this, amount);
		}
		return amount;
	}

	private boolean canAccept(TeamData data) {
		return data != null && data.canStartTasks(getQuest()) && checkTaskSequence(data) && !data.isCompleted(this);
	}

	@Override
	public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
		super.writeData(nbt, provider);

		nbt.putString("resource_type", resourceType.getTypeForSerialization());
		CompoundTag resource = new CompoundTag();
		writePayloadDataUnchecked(resourceType, resource, provider);
		nbt.put("resource", resource);
		nbt.putLong("required_rate", requiredRate);
		nbt.putLong("duration", duration);
		nbt.putInt("window", window);
		nbt.putInt("grace_ticks", graceTicks);
		nbt.putString("failure_mode", FailureMode.NAME_MAP.getName(failureMode));
		if (failureMode == FailureMode.REGRESS) {
			nbt.putLong("regression_rate", regressionRate);
		}
	}

	@Override
	public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
		super.readData(nbt, provider);

		resourceType = ThroughputTypes.ITEM;
		String typeStr = nbt.getString("resource_type");
		if (!typeStr.isEmpty()) {
			resourceType = ThroughputResourceType.get(parseResourceType(typeStr));
		}
		CompoundTag resource = nbt.getCompound("resource");
		resourcePayload = readPayloadDataUnchecked(resourceType, resource, provider);
		requiredRate = Math.max(1L, nbt.getLong("required_rate"));
		if (requiredRate == 0L) requiredRate = 8L; // Default
		duration = Math.max(1L, nbt.getLong("duration"));
		if (duration == 0L) duration = 1200L; // Default
		window = clamp(nbt.contains("window") ? nbt.getInt("window") : 20, 1, 1200);
		graceTicks = clamp(nbt.contains("grace_ticks") ? nbt.getInt("grace_ticks") : 20, 0, 1200);
		failureMode = FailureMode.NAME_MAP.get(nbt.getString("failure_mode"));
		regressionRate = Math.max(1L, nbt.getLong("regression_rate"));
		if (regressionRate == 0L) regressionRate = 20L; // Default
	}

	@Override
	public void writeNetData(RegistryFriendlyByteBuf buffer) {
		super.writeNetData(buffer);
		buffer.writeResourceLocation(resourceType.id());
		writePayloadNetUnchecked(resourceType, buffer);
		buffer.writeVarLong(requiredRate);
		buffer.writeVarLong(duration);
		buffer.writeVarInt(window);
		buffer.writeVarInt(graceTicks);
		FailureMode.NAME_MAP.write(buffer, failureMode);
		buffer.writeVarLong(regressionRate);
	}

	@Override
	public void readNetData(RegistryFriendlyByteBuf buffer) {
		super.readNetData(buffer);
		resourceType = ThroughputResourceType.get(buffer.readResourceLocation());
		resourcePayload = readPayloadNetUnchecked(resourceType, buffer);
		requiredRate = buffer.readVarLong();
		duration = buffer.readVarLong();
		window = buffer.readVarInt();
		graceTicks = buffer.readVarInt();
		failureMode = FailureMode.NAME_MAP.read(buffer);
		regressionRate = buffer.readVarLong();
	}

	@Override
	public void fillConfigGroup(ConfigGroup config) {
		super.fillConfigGroup(config);
		config.addEnum("resource_type", resourceType, this::setResourceTypeFromConfig, ThroughputResourceType.nameMap())
				.setNameKey("ftbquests.task.ftbquests.throughput.resource_type");

		fillPayloadConfigUnchecked(resourceType, config);

		config.addLong("required_rate", requiredRate, v -> requiredRate = v, 8L, 1L, Long.MAX_VALUE)
				.setNameKey("ftbquests.task.ftbquests.throughput.required_rate");
		config.addLong("duration", duration, v -> duration = v, 1200L, 1L, Long.MAX_VALUE)
				.setNameKey("ftbquests.task.ftbquests.throughput.duration");
		config.addInt("window", window, v -> window = v, 20, 1, 1200)
				.setNameKey("ftbquests.task.ftbquests.throughput.window");
		config.addInt("grace_ticks", graceTicks, v -> graceTicks = v, 20, 0, 1200)
				.setNameKey("ftbquests.task.ftbquests.throughput.grace_ticks");
		config.addEnum("failure_mode", failureMode, v -> failureMode = v, FailureMode.NAME_MAP)
				.setNameKey("ftbquests.task.ftbquests.throughput.failure_mode");
		if (failureMode == FailureMode.REGRESS) {
			config.addLong("regression_rate", regressionRate, v -> regressionRate = v, 20L, 1L, Long.MAX_VALUE)
					.setNameKey("ftbquests.task.ftbquests.throughput.regression_rate");
		}
	}

	@Override
	public void addMouseOverText(TooltipList list, TeamData teamData) {
		list.blankLine();
		list.add(Component.literal("Required: " + formatRequiredRate()).withStyle(ChatFormatting.GRAY));
		list.add(Component.literal("Duration: " + formatTicks(duration)).withStyle(ChatFormatting.GRAY));
		list.add(Component.literal("Window: " + formatTicks(window) + ", grace: " + formatTicks(graceTicks)).withStyle(ChatFormatting.GRAY));
		if (teamData != null) {
			ClientThroughputTelemetry.get(teamData.getTeamId(), id)
					.ifPresent(t -> list.add(Component.literal("Current: " + formatRate(t.currentRate())).withStyle(ChatFormatting.YELLOW)));
		}
	}

	private static ResourceLocation parseResourceType(String value) {
		return value.contains(":") ? ResourceLocation.tryParse(value) : FTBQuestsAPI.rl(value);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String formatTicks(long ticks) {
		if (ticks % 20L == 0L) {
			return StringUtils.formatDouble(ticks / 20.0, true) + "s";
		}
		return ticks + " ticks";
	}

	private void setResourceTypeFromConfig(ThroughputResourceType<?> newType) {
		if (resourceType == newType) {
			return;
		}
		resourceType = newType;
		resourcePayload = defaultPayloadUnchecked(newType);
	}

	private Object defaultPayloadUnchecked(ThroughputResourceType<?> type) {
		return type.defaultPayload();
	}

	@SuppressWarnings("unchecked")
	private <T> Icon getIconUnchecked(ThroughputResourceType<T> type) {
		return type.getIcon(this, (T) resourcePayload);
	}

	@SuppressWarnings("unchecked")
	private <T> Optional<PositionedIngredient> getIngredientUnchecked(ThroughputResourceType<T> type, Widget widget) {
		return type.getIngredient(widget, (T) resourcePayload);
	}

	@SuppressWarnings("unchecked")
	private <T> boolean acceptsItemUnchecked(ThroughputResourceType<T> type, ItemStack stack) {
		return type.acceptsItem(this, (T) resourcePayload, stack);
	}

	@SuppressWarnings("unchecked")
	private <T> boolean acceptsFluidUnchecked(ThroughputResourceType<T> type, Fluid fluid) {
		return type.acceptsFluid(this, (T) resourcePayload, fluid);
	}

	@SuppressWarnings("unchecked")
	private <T> boolean acceptsEnergyUnchecked(ThroughputResourceType<T> type) {
		return type.acceptsEnergy(this, (T) resourcePayload);
	}

	@SuppressWarnings("unchecked")
	private <T> void writePayloadDataUnchecked(ThroughputResourceType<T> type, CompoundTag nbt, HolderLookup.Provider provider) {
		type.writeData(nbt, (T) resourcePayload, provider);
	}

	@SuppressWarnings("unchecked")
	private <T> void writePayloadNetUnchecked(ThroughputResourceType<T> type, RegistryFriendlyByteBuf buffer) {
		type.writeNet(buffer, (T) resourcePayload);
	}

	private <T> Object readPayloadDataUnchecked(ThroughputResourceType<T> type, CompoundTag nbt, HolderLookup.Provider provider) {
		return type.readData(nbt, provider);
	}

	private <T> Object readPayloadNetUnchecked(ThroughputResourceType<T> type, RegistryFriendlyByteBuf buffer) {
		return type.readNet(buffer);
	}

	@SuppressWarnings("unchecked")
	private <T> void fillPayloadConfigUnchecked(ThroughputResourceType<T> type, ConfigGroup config) {
		type.fillConfigGroup(config, this, (T) resourcePayload);
	}

	public enum FailureMode {
		RESET("reset"),
		REGRESS("regress");

		public static final NameMap<FailureMode> NAME_MAP = NameMap.of(RESET, values())
				.id(v -> v.id)
				.baseNameKey("ftbquests.task.ftbquests.throughput.failure_mode")
				.create();

		private final String id;

		FailureMode(String id) {
			this.id = id;
		}
	}
}
