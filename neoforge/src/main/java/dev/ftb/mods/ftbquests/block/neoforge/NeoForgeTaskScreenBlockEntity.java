package dev.ftb.mods.ftbquests.block.neoforge;

import dev.ftb.mods.ftbquests.block.TaskScreenBlock;
import dev.ftb.mods.ftbquests.block.entity.TaskScreenBlockEntity;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.*;
import dev.ftb.mods.ftbquests.quest.task.neoforge.ForgeEnergyTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NeoForgeTaskScreenBlockEntity extends TaskScreenBlockEntity {
    @Nullable
    private AABB cachedRenderAABB = null;

    private final IItemHandler itemHandler = new TaskItemHandler();
    private final IFluidHandler fluidHandler = new TaskFluidHandler();
    private final IEnergyStorage energyHandler = new TaskEnergyHandler();

    public NeoForgeTaskScreenBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(blockPos, blockState);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TaskScreenBlockImpl.TYPE.get(), (be, side) -> ((NeoForgeTaskScreenBlockEntity) be).itemHandler);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TaskScreenBlockImpl.TYPE.get(), (be, side) -> ((NeoForgeTaskScreenBlockEntity) be).fluidHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TaskScreenBlockImpl.TYPE.get(), (be, side) -> ((NeoForgeTaskScreenBlockEntity) be).energyHandler);
    }

    public AABB getRenderBoundingBox() {
        if (cachedRenderAABB == null) {
            AABB box = new AABB(getBlockPos());
            if (!(getBlockState().getBlock() instanceof TaskScreenBlock tsb) || tsb.getSize() == 1) {
                cachedRenderAABB = box;
            } else {
                cachedRenderAABB = box.inflate(tsb.getSize());
            }
        }
        return cachedRenderAABB;
    }

    private class TaskItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot == 0 ? getTaskItem() : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot != 0) {
                return stack;
            }

            TeamData data = getCachedTeamData();
            if (getTask() instanceof ItemTask itemTask && data != null && data.canStartTasks(itemTask.getQuest())) {
                return itemTask.insert(data, stack, simulate);
            } else if (getTask() instanceof ThroughputTask throughputTask && data != null && throughputTask.acceptsItem(stack)) {
                long accepted = throughputTask.recordThroughput(data, ThroughputTypes.ITEM, (long) stack.getCount(), simulate);
                return stack.copyWithCount((int) (stack.getCount() - accepted));
            }

            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int count, boolean simulate) {
            if (count <= 0 || slot != 0 || isInputOnly()) {
                return ItemStack.EMPTY;
            }

            TeamData data = getCachedTeamData();
            if (getTask() instanceof ItemTask itemTask && data != null && data.canStartTasks(itemTask.getQuest()) && !data.isCompleted(itemTask) && !ItemMatchingSystem.INSTANCE.isItemFilter(itemTask.getItemStack())) {
                int itemsRemoved = (int) Math.min(data.getProgress(itemTask), count);
                if (!simulate) {
                    data.addProgress(itemTask, -itemsRemoved);
                }
                return itemTask.getItemStack().copyWithCount(itemsRemoved);
            }

            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && (getTask() instanceof ItemTask itemTask && itemTask.test(stack) || getTask() instanceof ThroughputTask throughputTask && throughputTask.acceptsItem(stack));
        }
    }

    private class TaskFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            if (getTask() instanceof FluidTask fluidTask && getCachedTeamData() != null) {
                return new FluidStack(fluidTask.getFluid(), (int) Math.min(Integer.MAX_VALUE, getCachedTeamData().getProgress(fluidTask)));
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            if (getTask() instanceof FluidTask fluidTask) {
                return (int) Math.min(Integer.MAX_VALUE, fluidTask.getMaxProgress());
            } else if (getTask() instanceof ThroughputTask throughputTask && throughputTask.getResourceType() == ThroughputTypes.FLUID) {
                return Integer.MAX_VALUE;
            }
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return getTask() instanceof FluidTask fluidTask && fluidTask.getFluid() == stack.getFluid()
                    || getTask() instanceof ThroughputTask throughputTask && throughputTask.acceptsFluid(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }

            TeamData data = getCachedTeamData();
            if (getTask() instanceof FluidTask fluidTask && data != null && data.canStartTasks(fluidTask.getQuest()) && !data.isCompleted(fluidTask) && fluidTask.getFluid() == resource.getFluid()) {
                int space = (int) Math.min(resource.getAmount(), fluidTask.getMaxProgress() - data.getProgress(fluidTask));
                if (space > 0 && action.execute()) {
                    data.addProgress(fluidTask, space);
                }
                return space;
            } else if (getTask() instanceof ThroughputTask throughputTask && data != null && throughputTask.acceptsFluid(resource.getFluid())) {
                long accepted = throughputTask.recordThroughput(data, ThroughputTypes.FLUID, (long) resource.getAmount(), action.simulate());
                return (int) accepted;
            }

            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private class TaskEnergyHandler implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            TeamData data = getCachedTeamData();
            if (getTask() instanceof ForgeEnergyTask energyTask && data != null && data.canStartTasks(energyTask.getQuest()) && !data.isCompleted(energyTask)) {
                int space = (int) Math.min(maxReceive, Math.min(energyTask.getMaxInput(), energyTask.getMaxProgress() - data.getProgress(energyTask)));
                if (space > 0 && !simulate) {
                    data.addProgress(energyTask, space);
                }
                return space;
            } else if (getTask() instanceof ThroughputTask throughputTask && data != null && throughputTask.acceptsEnergy()) {
                long accepted = throughputTask.recordThroughput(data, ThroughputTypes.ENERGY, (long) maxReceive, simulate);
                return (int) accepted;
            }
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            if (getTask() instanceof ForgeEnergyTask energyTask && getCachedTeamData() != null) {
                return (int) Math.min(Integer.MAX_VALUE, getCachedTeamData().getProgress(energyTask));
            }
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            if (getTask() instanceof ForgeEnergyTask energyTask) {
                return (int) Math.min(Integer.MAX_VALUE, energyTask.getMaxProgress());
            } else if (getTask() instanceof ThroughputTask throughputTask && throughputTask.getResourceType() == ThroughputTypes.ENERGY) {
                return Integer.MAX_VALUE;
            }
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
