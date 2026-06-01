package dev.ftb.mods.ftbquests.quest.task;

import dev.architectury.fluid.FluidStack;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Optional;

public class FluidThroughputType extends ThroughputResourceType<FluidThroughputType.FluidPayload> {
	public FluidThroughputType() {
		super(FTBQuestsAPI.rl("fluid"), "mB/s",
				() -> Icon.getIcon(Optional.ofNullable(ClientUtils.getStillTexture(FluidStack.create(Fluids.WATER, 1000L)))
								.map(ResourceLocation::toString)
								.orElse("missingno")).withTint(Color4I.rgb(0x8080FF))
						.combineWith(Icon.getIcon(FluidTask.TANK_TEXTURE.toString()))
		);
	}

	@Override
	public FluidPayload defaultPayload() {
		return new FluidPayload(FluidStack.create(Fluids.WATER, FluidStack.bucketAmount()));
	}

	@Override
	public void writeData(CompoundTag nbt, FluidPayload payload, HolderLookup.Provider provider) {
		nbt.put("fluid", payload.stack.write(provider, new CompoundTag()));
	}

	@Override
	public FluidPayload readData(CompoundTag nbt, HolderLookup.Provider provider) {
		return new FluidPayload(validFluidStack(FluidStack.read(provider, nbt.getCompound("fluid"))));
	}

	@Override
	public void writeNet(RegistryFriendlyByteBuf buffer, FluidPayload payload) {
		FluidStack.STREAM_CODEC.encode(buffer, payload.stack);
	}

	@Override
	public FluidPayload readNet(RegistryFriendlyByteBuf buffer) {
		return new FluidPayload(FluidStack.STREAM_CODEC.decode(buffer));
	}

	@Override
	public void fillConfigGroup(ConfigGroup config, ThroughputTask task, FluidPayload payload) {
		config.addFluidStack("fluid", payload.stack, v -> task.setResourcePayload(this, new FluidPayload(v)), false);
	}

	@Override
	public Icon getIcon(ThroughputTask task, FluidPayload payload) {
		return Icon.getIcon(Optional.ofNullable(ClientUtils.getStillTexture(payload.stack))
						.map(ResourceLocation::toString)
						.orElse("missingno")).withTint(Color4I.rgb(ClientUtils.getStillColor(payload.stack)))
				.combineWith(Icon.getIcon(FluidTask.TANK_TEXTURE.toString()));
	}

	@Override
	public Optional<PositionedIngredient> getIngredient(Widget widget, FluidPayload payload) {
		return PositionedIngredient.of(payload.stack, widget);
	}

	@Override
	public boolean acceptsFluid(ThroughputTask task, FluidPayload payload, Fluid fluid) {
		return payload.stack.getFluid() == fluid;
	}

	@Override
	public String formatRate(long rate) {
		return FluidTask.getVolumeString(rate) + "/s";
	}

	private static FluidStack validFluidStack(FluidStack stack) {
		return stack.getFluid() == Fluids.EMPTY || stack.getAmount() <= 0L ? FluidStack.create(Fluids.WATER, FluidStack.bucketAmount()) : stack;
	}

	public record FluidPayload(FluidStack stack) {
	}
}
