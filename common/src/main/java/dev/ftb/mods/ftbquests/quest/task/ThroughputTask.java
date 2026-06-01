package dev.ftb.mods.ftbquests.quest.task;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
import dev.ftb.mods.ftblibrary.client.util.PositionedIngredient;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftblibrary.platform.fluid.FluidStack;
import dev.ftb.mods.ftblibrary.util.NameMap;
import dev.ftb.mods.ftblibrary.util.StringUtils;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.client.ClientThroughputTelemetry;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
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
	public Icon<?> getAltIcon() {
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

	public Identifier getResourceTypeId() {
		return resourceType.id();
	}

	public ItemStack getItemStack() {
		return ((ItemThroughputType.ItemPayload) getPayload(ThroughputTypes.ITEM)).stack();
	}

	public FluidStack getFluidStack() {
		return ((FluidThroughputType.FluidPayload) getPayload(ThroughputTypes.FLUID)).stack();
	}

	public Fluid getFluid() {
		return getFluidStack().fluid();
	}

	public DataComponentPatch getFluidDataComponentPatch() {
		return getFluidStack().getComponents() instanceof PatchedDataComponentMap pdcm ? pdcm.asPatch() : DataComponentPatch.EMPTY;
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
	public void writeData(Json5Object json, HolderLookup.Provider provider) {
		super.writeData(json, provider);

		json.addProperty("resource_type", resourceType.getTypeForSerialization());
		Json5Object resource = new Json5Object();
		writePayloadJsonUnchecked(resourceType, resource, provider);
		json.add("resource", resource);
		json.addProperty("required_rate", requiredRate);
		json.addProperty("duration", duration);
		json.addProperty("window", window);
		json.addProperty("grace_ticks", graceTicks);
		json.addProperty("failure_mode", FailureMode.NAME_MAP.getName(failureMode));
		if (failureMode == FailureMode.REGRESS) {
			json.addProperty("regression_rate", regressionRate);
		}
	}

	@Override
	public void readData(Json5Object json, HolderLookup.Provider provider) {
		super.readData(json, provider);

		resourceType = ThroughputTypes.ITEM;
		Json5Util.getString(json, "resource_type")
				.map(ThroughputTask::parseResourceType)
				.ifPresent(id -> resourceType = ThroughputResourceType.get(id));
		Json5Object resource = Json5Util.getJson5Object(json, "resource").orElse(new Json5Object());
		resourcePayload = readPayloadJsonUnchecked(resourceType, resource, provider);
		requiredRate = Math.max(1L, Json5Util.getLong(json, "required_rate").orElse(8L));
		duration = Math.max(1L, Json5Util.getLong(json, "duration").orElse(1200L));
		window = clamp(Json5Util.getInt(json, "window").orElse(20), 1, 1200);
		graceTicks = clamp(Json5Util.getInt(json, "grace_ticks").orElse(20), 0, 1200);
		failureMode = Json5Util.getString(json, "failure_mode").map(FailureMode.NAME_MAP::get).orElse(FailureMode.RESET);
		regressionRate = Math.max(1L, Json5Util.getLong(json, "regression_rate").orElse(20L));
	}

	@Override
	public void writeNetData(RegistryFriendlyByteBuf buffer) {
		super.writeNetData(buffer);
		buffer.writeIdentifier(resourceType.id());
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
		resourceType = ThroughputResourceType.get(buffer.readIdentifier());
		resourcePayload = readPayloadNetUnchecked(resourceType, buffer);
		requiredRate = buffer.readVarLong();
		duration = buffer.readVarLong();
		window = buffer.readVarInt();
		graceTicks = buffer.readVarInt();
		failureMode = FailureMode.NAME_MAP.read(buffer);
		regressionRate = buffer.readVarLong();
	}

	@Override
	public void fillConfigGroup(EditableConfigGroup config) {
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

	private static Identifier parseResourceType(String value) {
		return value.contains(":") ? Identifier.tryParse(value) : FTBQuestsAPI.id(value);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String formatTicks(long ticks) {
		if (ticks % 20L == 0L) {
			return StringUtils.formatDouble(ticks / 20L, true) + "s";
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
		// These defaults keep the task valid even if the current screen is
		// closed before its conditional payload fields are rebuilt.
		// Do not reopen or rebuild EditConfigScreen from this setter. In this
		// FTB Library version that corrupts the parent-screen stack and traps
		// users in a reopen loop; users must accept and reopen to see new fields.
		return type.defaultPayload();
	}

	@SuppressWarnings("unchecked")
	private <T> Icon<?> getIconUnchecked(ThroughputResourceType<T> type) {
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
	private <T> void writePayloadJsonUnchecked(ThroughputResourceType<T> type, Json5Object json, HolderLookup.Provider provider) {
		type.writeJson(json, (T) resourcePayload, provider);
	}

	@SuppressWarnings("unchecked")
	private <T> void writePayloadNetUnchecked(ThroughputResourceType<T> type, RegistryFriendlyByteBuf buffer) {
		type.writeNet(buffer, (T) resourcePayload);
	}

	private <T> Object readPayloadJsonUnchecked(ThroughputResourceType<T> type, Json5Object json, HolderLookup.Provider provider) {
		return type.readJson(json, provider);
	}

	private <T> Object readPayloadNetUnchecked(ThroughputResourceType<T> type, RegistryFriendlyByteBuf buffer) {
		return type.readNet(buffer);
	}

	@SuppressWarnings("unchecked")
	private <T> void fillPayloadConfigUnchecked(ThroughputResourceType<T> type, EditableConfigGroup config) {
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
