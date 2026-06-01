package dev.ftb.mods.ftbquests.block.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import dev.ftb.mods.ftbquests.block.TaskScreenBlock;
import dev.ftb.mods.ftbquests.block.entity.TaskScreenBlockEntity;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.EnergyTask;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskScreenResourceConsumer;
import dev.ftb.mods.ftbquests.quest.task.ThroughputTask;
import dev.ftb.mods.ftbquests.quest.task.ThroughputTypes;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class NeoTaskScreenBlockEntity extends TaskScreenBlockEntity {
    @Nullable
    private AABB cachedRenderAABB = null;

    private final ResourceHandler<ItemResource> itemHandler = new TaskItemHandler();
    private final ResourceHandler<FluidResource> fluidHandler = new TaskFluidHandler();
    private final EnergyHandler energyHandler = new TaskEnergyHandler();

    public NeoTaskScreenBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(blockPos, blockState);
    }

    @Override
    public Task getTask() {
        return super.getTask();
    }

    @Override
    public void setTask(Task task) {
        super.setTask(task);
    }

    public ResourceHandler<ItemResource> getItemHandler() {
        return itemHandler;
    }

    public ResourceHandler<FluidResource> getFluidHandler() {
        return fluidHandler;
    }

    public EnergyHandler getEnergyHandler() {
        return energyHandler;
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

    private class TaskItemHandler implements ResourceHandler<ItemResource> {
        private long inserted;
        private final ProgressSnapshot snapshot = new ProgressSnapshot(() -> inserted, a -> inserted = a);

        @Override
        public int size() {
            return 2;
        }

        @Override
        public ItemResource getResource(int slot) {
            if (slot == 0) {
                if (getTask() instanceof ItemTask itemTask) {
                    return ItemResource.of(itemTask.getItemStack());
                } else if (getTask() instanceof ThroughputTask throughputTask && throughputTask.getResourceType() == ThroughputTypes.ITEM) {
                    return ItemResource.of(throughputTask.getItemStack());
                }
            }
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int slot) {
            if (getTask() instanceof ItemTask itemTask) {
                return Math.min(getCachedTeamData().getProgress(itemTask), itemTask.getItemStack().getMaxStackSize());
            }

            return 0;
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return resource.getMaxStackSize();
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return getTask() instanceof ItemTask itemTask && itemTask.test(resource.toStack())
                    || getTask() instanceof ThroughputTask throughputTask && throughputTask.acceptsItem(resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, @NonNull TransactionContext transaction) {
            TeamData data = getCachedTeamData();
            ItemStack stack = resource.toStack(amount);
            if (getTask() instanceof ItemTask itemTask && data.canStartTasks(itemTask.getQuest())) {
                // task.insert() handles testing the item is valid and the task isn't already completed
                ItemStack res = itemTask.insert(data, stack, true);
                int nAdded = stack.getCount() - res.getCount();
                if (nAdded > 0) {
                    this.snapshot.updateSnapshots(transaction);
                    inserted += nAdded;
                }
                return nAdded;
            } else if (getTask() instanceof ThroughputTask t && data != null && t.acceptsItem(stack)) {
                long nAdded = t.recordThroughput(data, ThroughputTypes.ITEM, (long)stack.getCount(), true);
                if (nAdded > 0L) {
                    this.snapshot.updateSnapshots(transaction);
                    inserted += nAdded;
                }
                return Math.toIntExact(nAdded);
            }
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (getTask() instanceof ItemTask itemTask && !isInputOnly() && !ItemMatchingSystem.INSTANCE.isItemFilter(itemTask.getItemStack())) {
                TeamData data = getCachedTeamData();
                if (data != null && data.canStartTasks(itemTask.getQuest()) && !data.isCompleted(itemTask)) {
                    int nRemoved = (int) Math.min(data.getProgress(itemTask) - inserted, amount);
                    if (nRemoved > 0) {
                        this.snapshot.updateSnapshots(transaction);
                        inserted -= nRemoved;
                    }
                    return nRemoved;
                }
            }
            return 0;
        }
    }

    private class TaskFluidHandler implements ResourceHandler<FluidResource> {
        private long inserted;
        private final ProgressSnapshot snapshot = new ProgressSnapshot(() -> inserted, a -> inserted = a);

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int i) {
            if (getTask() instanceof FluidTask fluidTask) {
                return FluidResource.of(fluidTask.getFluid(), fluidTask.getFluidDataComponentPatch());
            } else if (getTask() instanceof ThroughputTask throughputTask && throughputTask.getResourceType() == ThroughputTypes.FLUID) {
                return FluidResource.of(throughputTask.getFluid(), throughputTask.getFluidDataComponentPatch());
            }
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int i) {
            return getTask() instanceof FluidTask fluidTask && getCachedTeamData() != null ? getCachedTeamData().getProgress(fluidTask) : 0L;
        }

        @Override
        public long getCapacityAsLong(int i, FluidResource resource) {
            if (getTask() instanceof FluidTask t) {
                return t.getMaxProgress();
            } else if (getTask() instanceof ThroughputTask throughputTask && throughputTask.getResourceType() == ThroughputTypes.FLUID) {
                return Long.MAX_VALUE;
            }
            return 0L;
        }

        @Override
        public boolean isValid(int i, FluidResource resource) {
            return getTask() instanceof FluidTask fluidTask && fluidTask.getFluid() == resource.getFluid()
                    || getTask() instanceof ThroughputTask throughputTask && throughputTask.acceptsFluid(resource.getFluid());
        }

        @Override
        public int insert(int i, FluidResource resource, int amount, TransactionContext transaction) {
            if (getTask() instanceof FluidTask fluidTask) {
                TeamData data = getCachedTeamData();
                if (data != null && data.canStartTasks(fluidTask.getQuest()) && !data.isCompleted(fluidTask) && fluidTask.getFluid() == resource.getFluid()) {
                    long curProgress = data.getProgress(fluidTask) + inserted;
                    long space = fluidTask.getMaxProgress() - curProgress;
                    long toAdd = Math.min(amount, space);
                    if (toAdd > 0L) {
                        this.snapshot.updateSnapshots(transaction);
                        inserted += toAdd;
                    }
                    return Math.toIntExact(toAdd);
                }
            } else if (getTask() instanceof ThroughputTask t && t.acceptsFluid(resource.getFluid())) {
                TeamData data = getCachedTeamData();
                if (data != null) {
                    long toAdd = t.recordThroughput(data, ThroughputTypes.FLUID, (long)amount, true);
                    if (toAdd > 0L) {
                        this.snapshot.updateSnapshots(transaction);
                        inserted += toAdd;
                    }
                    return Math.toIntExact(toAdd);
                }
            }

            return 0;
        }

        @Override
        public int extract(int i, FluidResource resource, int amount, TransactionContext transaction) {
            if (getTask() instanceof FluidTask fluidTask) {
                TeamData data = getCachedTeamData();
                if (data != null && data.canStartTasks(fluidTask.getQuest()) && !data.isCompleted(fluidTask)) {
                    long curProgress = data.getProgress(fluidTask) + inserted;
                    long toTake = Math.min(amount, curProgress);
                    if (toTake > 0L) {
                        this.snapshot.updateSnapshots(transaction);
                        inserted -= toTake;
                    }
                    return Math.toIntExact(toTake);
                }
            }

            return 0;
        }
    }

    private class TaskEnergyHandler implements EnergyHandler {
        private long inserted;
        private final ProgressSnapshot snapshot = new ProgressSnapshot(() -> inserted, a -> inserted = a);

        @Override
        public long getAmountAsLong() {
            return getTask() instanceof EnergyTask energyTask && getCachedTeamData() != null ? (int) getCachedTeamData().getProgress(energyTask) : 0L;
        }

        @Override
        public long getCapacityAsLong() {
            if (getTask() instanceof EnergyTask energyTask) {
                return energyTask.getValue();
            } else if (getTask() instanceof ThroughputTask throughputTask && throughputTask.getResourceType() == ThroughputTypes.ENERGY) {
                return Long.MAX_VALUE;
            }
            return 0L;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (getTask() instanceof EnergyTask energyTask) {
                TeamData data = getCachedTeamData();
                if (data != null && data.canStartTasks(energyTask.getQuest()) && !data.isCompleted(energyTask)) {
                    long space = energyTask.getMaxProgress() - data.getProgress(energyTask) - inserted;
                    long toInsert = Math.min(energyTask.getMaxInput(), Math.min(amount, space));
                    if (toInsert > 0L) {
                        this.snapshot.updateSnapshots(transaction);
                        inserted += toInsert;
                    }
                    return Math.toIntExact(toInsert);
                }
            } else if (getTask() instanceof ThroughputTask t && t.acceptsEnergy()) {
                TeamData data = getCachedTeamData();
                if (data != null) {
                    long toInsert = t.recordThroughput(data, ThroughputTypes.ENERGY, (long)amount, true);
                    if (toInsert > 0L) {
                        this.snapshot.updateSnapshots(transaction);
                        inserted += toInsert;
                    }
                    return Math.toIntExact(toInsert);
                }
            }
            return 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    private class ProgressSnapshot extends SnapshotJournal<Long> {
        private final LongSupplier progressGetter;
        private final LongConsumer progressSetter;

        public ProgressSnapshot(LongSupplier progressGetter, LongConsumer progressSetter) {
            this.progressGetter = progressGetter;
            this.progressSetter = progressSetter;
        }

        @Override
        protected Long createSnapshot() {
            return progressGetter.getAsLong();
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            progressSetter.accept(snapshot);
        }

        @Override
        protected void onRootCommit(Long originalState) {
            long inserted = progressGetter.getAsLong();
            if (!originalState.equals(inserted)) {
                TeamData data = getCachedTeamData();
                if (data != null && getTask() instanceof TaskScreenResourceConsumer consumer) {
                    if (inserted > 0L) {
                        consumer.recordThroughput(data, consumer.getResourceType(), inserted, false);
                    }
                } else if (data != null) {
                    data.setProgress(getTask(), data.getProgress(getTask()) + inserted);
                }
                progressSetter.accept(0L);
            }
        }
    }
}
