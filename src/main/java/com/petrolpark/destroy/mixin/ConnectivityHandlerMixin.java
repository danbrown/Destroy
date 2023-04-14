package com.petrolpark.destroy.mixin;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.petrolpark.destroy.behaviour.PollutingBehaviour;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.tileEntity.IMultiTileContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

@Mixin(ConnectivityHandler.class)
public class ConnectivityHandlerMixin {
    
    /**
     * This is called whenever a Fluid Tank is destroyed. It's purpose is to determine if any Fluid
     * had to be voided because it wouldn't fit, and if so, pollute the Level accordingly.
     * It's mostly all copied from {@link com.simibubi.create.api.connectivity.ConnectivityHandler this internal splitMulti method}.
     * @param <T> A type of Block Entity (typically Fluid Tank)
     * @param be The BE which was destroyed
     * @param ci The callback
     */
    @Inject(method = "splitMulti", at = @At(value="HEAD"))
    private static <T extends BlockEntity & IMultiTileContainer> void inSplitMulti(T be, CallbackInfo ci) {

        BlockPos startPos = be.getBlockPos();

        Level level = be.getLevel();
		if (level == null) return; // Stop if this isn't in a Level

		be = be.getControllerTE();
		if (be == null) return; // Stop if there's no controller

		int height = be.getHeight();
		int width = be.getWidth();
		if (width == 1 && height == 1) return; // Stop if this was the only BE in the multi

		BlockPos origin = be.getBlockPos(); // The BE which has been destroyed
		Direction.Axis axis = be.getMainConnectionAxis();

		FluidStack toDistribute = FluidStack.EMPTY;
		int maxCapacity = 0;
		if (!(be instanceof IMultiTileContainer.Fluid ifluidBE && ifluidBE.hasTank())) return;
        toDistribute = ifluidBE.getFluid(0); // Get the Fluid which has to be redistributed
        maxCapacity = ifluidBE.getTankSize(0); // Get how much Fluid can fit in each Tank (I think?)

        if (!toDistribute.isEmpty() && !be.isRemoved()) { // If there's Fluid to be doled out
            toDistribute.shrink(maxCapacity); // I have no clue why this needs to be done but the right numbers come out at the end so I don't want to know

            // Check every BE in the multi
            for (int yOffset = 0; yOffset < height; yOffset++) {
                for (int xOffset = 0; xOffset < width; xOffset++) {
                    for (int zOffset = 0; zOffset < width; zOffset++) {
                        
                        BlockPos pos = switch (axis) { // Adjust what block we're looking for depending on which way the multi is facing - as Fluid Tanks can only be vertical I don't think this is too necessary 
                            case X -> origin.offset(yOffset, xOffset, zOffset);
                            case Y -> origin.offset(xOffset, yOffset, zOffset);
                            case Z -> origin.offset(xOffset, zOffset, yOffset);
                        };
                        
                        T partAt = ConnectivityHandler.partAt(be.getType(), level, pos); // Get this BE in the multi

                        if (partAt == null) continue; // If it's not here, continue
                        if (!partAt.getController().equals(origin)) continue; // If it has a different controller, ignore it

                        if (!toDistribute.isEmpty() && partAt != be) { // If there's still Fluid to be doled out and this isn't the controller we just destroyed
                            int split = Math.min(maxCapacity, toDistribute.getAmount()); // Calculate how much Fluid we can fit in this BE
                            toDistribute.shrink(split); // Decrease the remaining Fluid to dole out by that amount
                        };
                    };
                };
            };

            if (toDistribute.getAmount() > 0) PollutingBehaviour.pollute(level, startPos, toDistribute);
        };
    };

};