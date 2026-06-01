package dev.ftb.mods.ftbquests.quest.task;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
import dev.ftb.mods.ftblibrary.client.util.ClientUtils;
import dev.ftb.mods.ftblibrary.client.util.PositionedIngredient;
import dev.ftb.mods.ftblibrary.client.util.TextureAtlasSpriteRef;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.json5.Json5Ops;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftblibrary.platform.Platform;
import dev.ftb.mods.ftblibrary.platform.fluid.FluidStack;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

public class FluidThroughputType extends ThroughputResourceType<FluidThroughputType.FluidPayload> {
	public FluidThroughputType() {
		super(FTBQuestsAPI.id("fluid"), "mB/s", () -> Icon.getIcon("minecraft:item/water_bucket"));
	}

	@Override
	public FluidPayload defaultPayload() {
		return new FluidPayload(defaultFluidStack());
	}

	@Override
	public void writeJson(Json5Object json, FluidPayload payload, HolderLookup.Provider provider) {
		json.add("fluid", FluidStack.CODEC.encodeStart(provider.createSerializationContext(Json5Ops.INSTANCE), payload.stack).getOrThrow());
	}

	@Override
	public FluidPayload readJson(Json5Object json, HolderLookup.Provider provider) {
		FluidStack stack = Json5Util.getJson5Object(json, "fluid")
				.flatMap(o -> FluidStack.CODEC.parse(provider.createSerializationContext(Json5Ops.INSTANCE), o).result())
				.orElseGet(FluidThroughputType::defaultFluidStack);
		return new FluidPayload(validFluidStack(stack));
	}

	@Override
	public void writeNet(RegistryFriendlyByteBuf buffer, FluidPayload payload) {
		FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.stack);
	}

	@Override
	public FluidPayload readNet(RegistryFriendlyByteBuf buffer) {
		return new FluidPayload(validFluidStack(FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
	}

	@Override
	public void fillConfigGroup(EditableConfigGroup config, ThroughputTask task, FluidPayload payload) {
		config.addFluidStack("fluid", payload.stack, v -> task.setResourcePayload(this, new FluidPayload(validFluidStack(v))), defaultFluidStack(), false)
				.setNameKey("ftbquests.task.ftbquests.fluid");
	}

	@Override
	public Icon<?> getIcon(ThroughputTask task, FluidPayload payload) {
		return new TextureAtlasSpriteRef(ClientUtils.getStillTexture(payload.stack))
				.createIcon(Color4I.rgb(ClientUtils.getFluidColor(payload.stack)));
	}

	@Override
	public Optional<PositionedIngredient> getIngredient(Widget widget, FluidPayload payload) {
		return PositionedIngredient.of(payload.stack, widget);
	}

	@Override
	public boolean acceptsFluid(ThroughputTask task, FluidPayload payload, Fluid fluid) {
		return payload.stack.fluid() == fluid;
	}

	@Override
	public String formatRate(long rate) {
		return FluidTask.getVolumeString(rate) + "/s";
	}

	private static FluidStack defaultFluidStack() {
		return new FluidStack(Fluids.WATER, Platform.get().misc().bucketFluidAmount());
	}

	private static FluidStack validFluidStack(FluidStack stack) {
		return stack.fluid() == Fluids.EMPTY || stack.amount() <= 0L ? defaultFluidStack() : stack;
	}

	public record FluidPayload(FluidStack stack) {
	}
}
