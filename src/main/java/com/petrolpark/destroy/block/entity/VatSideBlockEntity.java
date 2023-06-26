package com.petrolpark.destroy.block.entity;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.petrolpark.destroy.block.DestroyBlocks;
import com.petrolpark.destroy.capability.block.VatTankCapability;
import com.petrolpark.destroy.util.vat.Vat;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class VatSideBlockEntity extends CopycatBlockEntity implements IHaveGoggleInformation {

    protected SmartFluidTankBehaviour inputBehaviour;
    protected LazyOptional<IFluidHandler> fluidCapability;

    public Direction direction; // The outward direction this side is facing
    public BlockPos controllerPosition;

    protected DisplayType displayType;

    public VatSideBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        displayType = DisplayType.NORMAL;
    };

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        inputBehaviour = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.TYPE, this, 1, 1000, false)
            .whenFluidUpdates(this::tryInsertFluidInVat)
            .forbidExtraction();
        fluidCapability = LazyOptional.of(() -> {
            // Allow inputting into this side tank (which is then mixed into the main tank)
            LazyOptional<? extends IFluidHandler> inputCap = inputBehaviour.getCapability();
            // Allow outputting from the main tank
            LazyOptional<? extends IFluidHandler> outputCap = LazyOptional.empty();
            VatControllerBlockEntity vatController = getController();
            if (vatController != null) {
                outputCap = vatController.getBehaviour(SmartFluidTankBehaviour.TYPE).getCapability();
            };
			return new VatTankCapability(outputCap.orElse(null), inputCap.orElse(null));
        });
        //TODO check fluids can fit in the Vat
    };

    @Override
    public void destroy() {
        super.destroy();
        VatControllerBlockEntity vatController = getController();
        if (vatController != null && !vatController.underDeconstruction) vatController.deleteVat(getBlockPos());
    };

    @Nullable
    @SuppressWarnings("null")
    public VatControllerBlockEntity getController() {
        if (!hasLevel() || controllerPosition == null) return null;
        BlockEntity be = getLevel().getBlockEntity(controllerPosition);
        if (!(be instanceof VatControllerBlockEntity vatController)) return null;
        return vatController;
    };

    public Optional<Vat> getVat() {
        VatControllerBlockEntity vatController = getController();
        if (vatController == null) return Optional.empty();
        return vatController.getVat();
    };

    public void tryInsertFluidInVat() {
        VatControllerBlockEntity vatController = getController();
        if (vatController == null) return;
        // Attempt to transfer Fluid from this Vat Side Block Entity to the Controller's Tank, which is the main one
        inputBehaviour.allowExtraction();
        // Determine how much Fluid could be added to the main tank (this should usually be everything)
        int drained = vatController.addFluid(inputBehaviour.getPrimaryHandler().drain(1000, FluidAction.SIMULATE));
        // Drain that amount from this Vat Side Block Entity's Tank
        inputBehaviour.getPrimaryHandler().drain(drained, FluidAction.EXECUTE);
        inputBehaviour.forbidExtraction();
    };

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (tag.contains("Side")) {
            direction = Direction.values()[tag.getInt("Side")];
        } else {
            direction = null;
        }
        controllerPosition = NbtUtils.readBlockPos(tag.getCompound("ControllerPosition"));
        displayType = DisplayType.values()[tag.getInt("DisplayType")];
    };

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (direction != null) tag.putInt("Side", direction.ordinal());
        if (controllerPosition != null) tag.put("ControllerPosition", NbtUtils.writeBlockPos(controllerPosition));
        tag.putInt("DisplayType", displayType.ordinal());
    };

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && side == direction && (displayType == DisplayType.NORMAL || displayType == DisplayType.PIPE)) {
            return fluidCapability.cast();
        };
        return super.getCapability(cap, side);
    };

    @Override
    public void setMaterial(BlockState blockState) {
        if (blockState.is(DestroyBlocks.VAT_SIDE.get())) return;
        super.setMaterial(blockState);
    };

    public static enum DisplayType {
        NORMAL,
        BAROMETER,
        THERMOMETER,
        PIPE
    };

    public DisplayType getDisplayType() {
        return displayType;
    };

    public void setDisplayType(DisplayType displayType) {
        if (this.displayType == displayType) return;
        this.displayType = displayType;
        if (!hasLevel()) return;
        getBlockState().updateNeighbourShapes(getLevel(), getBlockPos(), 3);
        updateDisplayType(getBlockPos().relative(direction)); // Check for a Pipe
        notifyUpdate();
    };

    @SuppressWarnings("null")
    public void updateDisplayType(BlockPos neighborPos) {
        if (getController() == null) return;
        FluidTransportBehaviour behaviour = BlockEntityBehaviour.get(getLevel(), neighborPos, FluidTransportBehaviour.TYPE);
        boolean nextToPipe = behaviour == null ? false : behaviour.canHaveFlowToward(getBlockState(), direction) || behaviour.canPullFluidFrom(getController().getTank().getFluid(), getBlockState(), direction);
        if (getDisplayType() == DisplayType.NORMAL && nextToPipe) {
            setDisplayType(DisplayType.PIPE);
        } else if (getDisplayType() == DisplayType.PIPE && !nextToPipe) {
            setDisplayType(DisplayType.NORMAL);
        };
    };

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        VatControllerBlockEntity vatController = getController();
        if (vatController == null) return false;
        return vatController.addToGoggleTooltip(tooltip, isPlayerSneaking);
    };
};