package io.github.chaosunity.ic.blockentity;

import io.github.chaosunity.ic.api.fluid.FluidHelper;
import io.github.chaosunity.ic.api.fluid.FluidStack;
import io.github.chaosunity.ic.blocks.BoilerBlock;
import io.github.chaosunity.ic.blocks.MachineVariant;
import io.github.chaosunity.ic.client.screen.BoilerScreenHandler;
import io.github.chaosunity.ic.objects.BlockEntities;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BoilerBlockEntity extends MachineBlockEntity<BoilerBlockEntity, BoilerBlock>
        implements ExtendedScreenHandlerFactory, ImplementedInventory, ImplementedFluidContainer {
    public static final int MAX_WATER_CAPACITY = 10000;
    public static final int MAX_STEAM_CAPACITY = 10000;
    public static final int[] TRANSFORM_RATE = new int[]{
            20,
            40
    };

    public final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    public final DefaultedList<FluidStack> fluids = DefaultedList.copyOf(
            FluidStack.EMPTY,
            new FluidStack(Fluids.WATER, MAX_WATER_CAPACITY),
            new FluidStack(io.github.chaosunity.ic.objects.Fluids.STEAM, MAX_STEAM_CAPACITY)
    );

    private int burnTime;
    private int fuelTime;

    public BoilerBlockEntity(BlockPos pos, BlockState state, MachineVariant variant) {
        super(switch (variant) {
            case COPPER -> BlockEntities.COPPER_BOILER_BLOCK_ENTITY;
            case IRON -> BlockEntities.IRON_BOILER_BLOCK_ENTITY;
        }, pos, state);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        FluidHelper.readNBT(nbt, fluids);
        burnTime = nbt.getInt("BurnTime");
        fuelTime = nbt.getInt("FuelTime");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        Inventories.writeNbt(nbt, inventory);
        FluidHelper.writeNBT(nbt, fluids);
        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("FuelTime", fuelTime);
        return super.writeNbt(nbt);
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    public int getTransformRate() {
        return TRANSFORM_RATE[getVariant().ordinal()];
    }

    public static void tick(World world, BlockPos pos, BlockState state, BlockEntity be) {
        if (world.isClient) return;

        if (be instanceof BoilerBlockEntity bbe) {
            var bl = bbe.isBurning();
            var changed = false;

            if (bbe.isBurning()) {
                if (!bbe.getWater().isEmpty() && !bbe.getSteam().isFull())
                    bbe.getSteam().transform(bbe.getWater(), bbe.getTransformRate());

                --bbe.burnTime;
            }

            var fuel = bbe.getFuel();
            if (!bbe.isBurning() && !bbe.getWater().isEmpty() && !bbe.getSteam().isFull()) {
                bbe.burnTime = bbe.getFuelTime(fuel);
                bbe.fuelTime = bbe.burnTime;

                if (bbe.isBurning()) {
                    changed = true;
                    if (!fuel.isEmpty()) {
                        Item item = fuel.getItem();
                        fuel.decrement(1);
                        if (fuel.isEmpty()) {
                            Item item2 = item.getRecipeRemainder();
                            bbe.inventory.set(0, item2 == null ? ItemStack.EMPTY : new ItemStack(item2));
                        }
                    }
                }
            }

            if (bl != bbe.isBurning()) {
                changed = true;
                state = state.with(BoilerBlock.LIT, bbe.isBurning());
                world.setBlockState(pos, state, 3);
            }

            if (changed) {
                markDirty(world, pos, state);
            }
        }
    }

    public MachineVariant getVariant() {
        return getVariant(this);
    }

    protected int getFuelTime(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        } else {
            Item item = fuel.getItem();
            return AbstractFurnaceBlockEntity.createFuelTimeMap().getOrDefault(item, 0);
        }
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    public DefaultedList<FluidStack> getContainers() {
        return fluids;
    }

    public ItemStack getFuel() {
        return getItems().get(0);
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getFuelTime() {
        return fuelTime;
    }

    public FluidStack getWater() {
        return getContainers().get(0);
    }

    public FluidStack getSteam() {
        return getContainers().get(1);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[]{0};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction direction) {
        return true;
    }

    @Override
    public boolean canInsertFluid(int index, Direction direction) {
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction direction) {
        return true;
    }

    @Override
    public boolean canExtractFluid(int index, Direction direction) {
        return false;
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return AbstractFurnaceBlockEntity.canUseAsFuel(stack);
    }

    @Override
    public Text getDisplayName() {
        return new TranslatableText(getCachedState().getBlock().getTranslationKey());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new BoilerScreenHandler(syncId, inv, this);
    }
}
