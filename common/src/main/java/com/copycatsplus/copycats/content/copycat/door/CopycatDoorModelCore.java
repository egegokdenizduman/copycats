package com.copycatsplus.copycats.content.copycat.door;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.simibubi.create.foundation.block.connected.CTModel;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace.*;

public class CopycatDoorModelCore extends CopycatModelCore {

    @Override
    public void registerModels(List<ModelEntry> entries) {
        entries.add(new ModelEntry(MATERIAL_KEY, ModelGetter.MATERIAL, this, updatePropertiesIfMatch(DoorBlock.class), EntryType.COPYCAT));
    }

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        if (material.getBlock() instanceof DoorBlock) {
            context.assembleAll();
            return;
        }

        if (state.getValue(CopycatDoorBlock.CT)) {
            assembleWithCT(state, context, getModelOf(material) instanceof CTModel);
        } else {
            assembleWithoutCT(state, context);
        }
    }

    // Plain tiles reuse their middle to hide the seam. Connected tiles already have none, so keep them in place
    private static void assembleWithCT(BlockState state, CopycatRenderContext context, boolean connected) {
        int rot = (int) state.getValue(DoorBlock.FACING).toYRot();
        boolean rightHinge = state.getValue(DoorBlock.HINGE).equals(DoorHingeSide.RIGHT);
        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
        boolean open = state.getValue(DoorBlock.OPEN);
        int lowerSeamY = connected ? 12 : 4;
        int upperSeamY = connected ? 0 : 8;
        int seamCull = connected ? UP | DOWN : 0;
        AssemblyTransform transform = t -> t.rotateY(rot);
        if (half == DoubleBlockHalf.LOWER) {
            if (!open) {
                //Front
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(16, 12, 2),
                        cull(SOUTH | UP));
                context.assemblePiece(transform,
                        vec3(0, 12, 0),
                        aabb(16, 4, 2).move(0, lowerSeamY, 0),
                        cull(SOUTH | DOWN | seamCull));
                //Back
                context.assemblePiece(transform,
                        vec3(0, 0, 2),
                        aabb(16, 12, 1).move(0, 0, 15),
                        cull(NORTH | UP));
                context.assemblePiece(transform,
                        vec3(0, 12, 2),
                        aabb(16, 4, 1).move(0, lowerSeamY, 15),
                        cull(NORTH | DOWN | seamCull));
            } else {
                if (!rightHinge) {
                    transform = t -> t.flipX(true).rotateY(rot);
                }
                //Front
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(2, 12, 16),
                        cull(EAST | UP));
                context.assemblePiece(transform,
                        vec3(0, 12, 0),
                        aabb(2, 4, 16).move(0, lowerSeamY, 0),
                        cull(EAST | DOWN | seamCull));
                //Back
                context.assemblePiece(transform,
                        vec3(2, 0, 0),
                        aabb(1, 12, 16).move(15, 0, 0),
                        cull(WEST | UP));
                context.assemblePiece(transform,
                        vec3(2, 12, 0),
                        aabb(1, 4, 16).move(15, lowerSeamY, 0),
                        cull(WEST | DOWN | seamCull));
            }
        } else {
            if (!open) {
                //Front
                context.assemblePiece(transform,
                        vec3(0, 4, 0),
                        aabb(16, 12, 2).move(0, 4, 0),
                        cull(SOUTH | DOWN));
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(16, 4, 2).move(0, upperSeamY, 0),
                        cull(SOUTH | UP | seamCull));
                //Back
                context.assemblePiece(transform,
                        vec3(0, 4, 2),
                        aabb(16, 12, 1).move(0, 4, 15),
                        cull(NORTH | DOWN));
                context.assemblePiece(transform,
                        vec3(0, 0, 2),
                        aabb(16, 4, 1).move(0, upperSeamY, 15),
                        cull(NORTH | UP | seamCull));
            } else {
                if (!rightHinge) {
                    transform = t -> t.flipX(true).rotateY(rot);
                }
                //Front
                context.assemblePiece(transform,
                        vec3(0, 4, 0),
                        aabb(2, 12, 16).move(0, 4, 0),
                        cull(EAST | DOWN));
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(2, 4, 16).move(0, upperSeamY, 0),
                        cull(EAST | UP | seamCull));
                //Back
                context.assemblePiece(transform,
                        vec3(2, 4, 0),
                        aabb(1, 12, 16).move(15, 4, 0),
                        cull(WEST | DOWN));
                context.assemblePiece(transform,
                        vec3(2, 0, 0),
                        aabb(1, 4, 16).move(15, upperSeamY, 0),
                        cull(WEST | UP | seamCull));
            }
        }
    }

    private static void assembleWithoutCT(BlockState state, CopycatRenderContext context) {
        int rot = (int) state.getValue(DoorBlock.FACING).toYRot();
        boolean rightHinge = state.getValue(DoorBlock.HINGE).equals(DoorHingeSide.RIGHT);
        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
        boolean open = state.getValue(DoorBlock.OPEN);
        AssemblyTransform transform = t -> t.rotateY(rot);
        if (half == DoubleBlockHalf.LOWER) {
            if (!open) {
                //Front
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(16, 16, 2),
                        cull(SOUTH));
                //Back
                context.assemblePiece(transform,
                        vec3(0, 0, 2),
                        aabb(16, 16, 1).move(0, 0, 15),
                        cull(NORTH));
            } else {
                if (!rightHinge) {
                    transform = t -> t.flipX(true).rotateY(rot);
                }
                //Front
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(2, 16, 16),
                        cull(EAST));
                //Back
                context.assemblePiece(transform,
                        vec3(2, 0, 0),
                        aabb(1, 16, 16).move(15, 0, 0),
                        cull(WEST));
            }
        } else {
            if (!open) {
                //Front
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(16, 16, 2).move(0, 0, 0),
                        cull(SOUTH));
                //Back
                context.assemblePiece(transform,
                        vec3(0, 0, 2),
                        aabb(16, 16, 1).move(0, 0, 15),
                        cull(NORTH));
            } else {
                if (!rightHinge) {
                    transform = t -> t.flipX(true).rotateY(rot);
                }
                //Front
                context.assemblePiece(transform,
                        vec3(0, 0, 0),
                        aabb(2, 16, 16).move(0, 0, 0),
                        cull(EAST));
                //Back
                context.assemblePiece(transform,
                        vec3(2, 0, 0),
                        aabb(1, 16, 16).move(15, 0, 0),
                        cull(WEST));
            }
        }
    }
}
