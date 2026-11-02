package com.copycatsplus.copycats.mixin.foundation.copycat;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.CopycatExternalContext;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTType;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Patch Create's {@link CTModel} to consider our copycats.
 */
@Mixin(CTModel.class)
public class CTModelMixin {
    @WrapOperation(
            method = "createCTData",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;")
    )
    private Block createCTData(BlockState instance, Operation<Block> original) {
        if (instance.getBlock() instanceof ICopycatBlock fcb && !(instance.getBlock() instanceof CopycatBlock)) {
            CCBlocks.WRAPPED_COPYCAT.get().setWrapped(fcb);
            return CCBlocks.WRAPPED_COPYCAT.get();
        }
        return original.call(instance);
    }

    @WrapOperation(
            method = "createCTData",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/block/connected/ConnectedTextureBehaviour;getDataType(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lcom/simibubi/create/foundation/block/connected/CTType;")
    )
    private CTType skipFacesWithoutPartialEdges(ConnectedTextureBehaviour instance, BlockAndTintGetter world, BlockPos pos, BlockState state, Direction face, Operation<CTType> original) {
        // Only faces with partial edge regions read the second result
        if (CopycatExternalContext.skipsPartialEdgeFace(face))
            return null;
        return original.call(instance, world, pos, state, face);
    }
}