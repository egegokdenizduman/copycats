package com.copycatsplus.copycats.content.copycat.sliding_door;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.simibubi.create.content.decoration.palettes.WindowBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.foundation.block.connected.CTModel;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace.*;

public class CopycatFoldingDoorModelCore extends CopycatModelCore {

    private final boolean left;
    private final boolean kinetic;

    public CopycatFoldingDoorModelCore(boolean left, boolean kinetic) {
        this.left = left;
        this.kinetic = kinetic;
    }

    @Override
    public void registerModels(List<ModelEntry> entries) {
        entries.add(new ModelEntry(MATERIAL_KEY, ModelGetter.MATERIAL, this, kinetic ? EntryType.KINETIC_COPYCAT : EntryType.COPYCAT));
    }

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        if (!kinetic && !state.getValue(SlidingDoorBlock.VISIBLE)) {
            return;
        }
        // A leaf is too narrow for the whole tile. Windows keep their middle so the glass stays, others keep their edges
        boolean window = material.getBlock() instanceof WindowBlock;
        int leftX = window ? 4 : 0;
        int rightX = window ? 8 : 12;
        if (state.getValue(CopycatSlidingDoorBlock.CT)) {
            assembleWithCT(state, context, getModelOf(material) instanceof CTModel, leftX, rightX);
        } else {
            assembleWithoutCT(state, context, leftX, rightX);
        }
    }

    // Plain tiles reuse their middle to hide the seam. Connected tiles already have none, so keep them in place
    private void assembleWithCT(BlockState state, CopycatRenderContext context, boolean connected, int leftX, int rightX) {
        int lowerSeamY = connected ? 12 : 4;
        int upperSeamY = connected ? 0 : 8;
        int seamCull = connected ? UP | DOWN : 0;
        for (boolean left : Iterate.falseAndTrue) {
            if (kinetic && left != this.left)
                continue;

            // The renderer handles rotation and left/right offset when animating
            // So transforms are only applied to the model when static
            Direction facing = state.getValue(DoorBlock.FACING);
            int rot = kinetic ? 270 : (int) facing.toYRot();
            int offset = left || kinetic ? 8 : 0;

            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            AssemblyTransform transform = t -> t.rotateY(rot);
            if (half == DoubleBlockHalf.LOWER) {
                //Front
                context.assemblePiece(transform,
                        vec3(offset, 0, 0),
                        aabb(4, 12, 2).move(leftX, 0, 0),
                        cull(SOUTH | UP | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 0),
                        aabb(4, 12, 2).move(rightX, 0, 0),
                        cull(SOUTH | UP | WEST));
                context.assemblePiece(transform,
                        vec3(offset, 12, 0),
                        aabb(4, 4, 2).move(leftX, lowerSeamY, 0),
                        cull(SOUTH | DOWN | seamCull | (kinetic ? UP : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 12, 0),
                        aabb(4, 4, 2).move(rightX, lowerSeamY, 0),
                        cull(SOUTH | DOWN | seamCull | (kinetic ? UP : 0) | WEST));
                //Back
                context.assemblePiece(transform,
                        vec3(offset, 0, 2),
                        aabb(4, 12, 1).move(leftX, 0, 15),
                        cull(NORTH | UP | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 2),
                        aabb(4, 12, 1).move(rightX, 0, 15),
                        cull(NORTH | UP | WEST));
                context.assemblePiece(transform,
                        vec3(offset, 12, 2),
                        aabb(4, 4, 1).move(leftX, lowerSeamY, 15),
                        cull(NORTH | DOWN | seamCull | (kinetic ? UP : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 12, 2),
                        aabb(4, 4, 1).move(rightX, lowerSeamY, 15),
                        cull(NORTH | DOWN | seamCull | (kinetic ? UP : 0) | WEST));
            } else {
                //Front
                context.assemblePiece(transform,
                        vec3(offset, 4, 0),
                        aabb(4, 12, 2).move(leftX, 4, 0),
                        cull(SOUTH | DOWN | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 4, 0),
                        aabb(4, 12, 2).move(rightX, 4, 0),
                        cull(SOUTH | DOWN | WEST));
                context.assemblePiece(transform,
                        vec3(offset, 0, 0),
                        aabb(4, 4, 2).move(leftX, upperSeamY, 0),
                        cull(SOUTH | UP | seamCull | (kinetic ? DOWN : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 0),
                        aabb(4, 4, 2).move(rightX, upperSeamY, 0),
                        cull(SOUTH | UP | seamCull | (kinetic ? DOWN : 0) | WEST));
                //Back
                context.assemblePiece(transform,
                        vec3(offset, 4, 2),
                        aabb(4, 12, 1).move(leftX, 4, 15),
                        cull(NORTH | DOWN | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 4, 2),
                        aabb(4, 12, 1).move(rightX, 4, 15),
                        cull(NORTH | DOWN | WEST));
                context.assemblePiece(transform,
                        vec3(offset, 0, 2),
                        aabb(4, 4, 1).move(leftX, upperSeamY, 15),
                        cull(NORTH | UP | seamCull | (kinetic ? DOWN : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 2),
                        aabb(4, 4, 1).move(rightX, upperSeamY, 15),
                        cull(NORTH | UP | seamCull | (kinetic ? DOWN : 0) | WEST));
            }
        }
    }

    private void assembleWithoutCT(BlockState state, CopycatRenderContext context, int leftX, int rightX) {
        for (boolean left : Iterate.falseAndTrue) {
            if (kinetic && left != this.left)
                continue;

            // The renderer handles rotation and left/right offset when animating
            // So transforms are only applied to the model when static
            Direction facing = state.getValue(DoorBlock.FACING);
            int rot = kinetic ? 270 : (int) facing.toYRot();
            int offset = left || kinetic ? 8 : 0;

            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            AssemblyTransform transform = t -> t.rotateY(rot);
            if (half == DoubleBlockHalf.LOWER) {
                //Front
                context.assemblePiece(transform,
                        vec3(offset, 0, 0),
                        aabb(4, 16, 2).move(leftX, 0, 0),
                        cull(SOUTH | (kinetic ? UP : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 0),
                        aabb(4, 16, 2).move(rightX, 0, 0),
                        cull(SOUTH | (kinetic ? UP : 0) | WEST));
                //Back
                context.assemblePiece(transform,
                        vec3(offset, 0, 2),
                        aabb(4, 16, 1).move(leftX, 0, 15),
                        cull(NORTH | (kinetic ? UP : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 2),
                        aabb(4, 16, 1).move(rightX, 0, 15),
                        cull(NORTH | (kinetic ? UP : 0) | WEST));
            } else {
                //Front
                context.assemblePiece(transform,
                        vec3(offset, 0, 0),
                        aabb(4, 16, 2).move(leftX, 0, 0),
                        cull(SOUTH | (kinetic ? DOWN : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 0),
                        aabb(4, 16, 2).move(rightX, 0, 0),
                        cull(SOUTH | (kinetic ? DOWN : 0) | WEST));
                //Back
                context.assemblePiece(transform,
                        vec3(offset, 0, 2),
                        aabb(4, 16, 1).move(leftX, 0, 15),
                        cull(NORTH | (kinetic ? DOWN : 0) | EAST));
                context.assemblePiece(transform,
                        vec3(offset + 4, 0, 2),
                        aabb(4, 16, 1).move(rightX, 0, 15),
                        cull(NORTH | (kinetic ? DOWN : 0) | WEST));
            }
        }
    }
}
