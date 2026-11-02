package com.copycatsplus.copycats.content.copycat.slope_layer;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeModelCore;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

public class CopycatSlopeLayerModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        int layer = state.getValue(CopycatSlopeLayerBlock.LAYERS);
        Direction facing = state.getValue(CopycatSlopeLayerBlock.FACING);
        Half half = state.getValue(CopycatSlopeLayerBlock.HALF);
        boolean in_wall = state.getValue(CopycatSlopeLayerBlock.IN_WALL);
        double low = CopycatSlopeLayerBlock.getLowEdge(layer) * 4;
        double high = CopycatSlopeLayerBlock.getHighEdge(layer) * 4;

        if (CopycatSlopeLayerBlock.isCorner(state)) {
            StairsShape shape = state.getValue(CopycatSlopeLayerBlock.SHAPE);
            boolean flipX = shape == StairsShape.INNER_RIGHT || shape == StairsShape.OUTER_RIGHT;
            boolean inner = shape == StairsShape.INNER_LEFT || shape == StairsShape.INNER_RIGHT;
            AssemblyTransform corner = t -> t.flipX(flipX).flipY(half == Half.TOP).rotateY((int) facing.toYRot());
            CopycatSlopeModelCore.assembleCorner(context, corner, inner, low, high, enhanced);
            return;
        }

        AssemblyTransform transform = t -> {
            if (CopycatSlopeLayerBlock.isVertical(state)) {
                t.rotateZ(-90).flipX(half == Half.TOP).rotateY((int) facing.toYRot());
                return;
            }

            t.rotateY((int) facing.toYRot()).flipY(half == Half.TOP);

            if (in_wall) {
                switch (facing){
                    case NORTH -> t.rotateX(half == Half.TOP ? 270 : 90);
                    case SOUTH -> t.rotateX(half == Half.TOP ? 90 : 270);
                    case WEST -> t.rotateZ(half == Half.TOP ? 90 : 270);
                    case EAST -> t.rotateZ(half == Half.TOP ? 270 : 90);
                }
            }
        };

        double kink = CopycatSlopeLayerBlock.getKink(layer);
        if (kink != 0) {
            CopycatSlopeModelCore.assembleKinkedSlope(context, transform, low, high, kink);
            // steep run pieces have no edge band of their own, so the upper piece of a pair draws it along the slope,
            // down past its own block over the lower piece, whose side walls connect upwards and show no border
            if (enhanced && low == 0) {
                if ((context.variant() & CopycatSlopeLayerBlock.RUN_LOW) != 0)
                    CopycatSlopeModelCore.assembleRunEdge(context, transform, 0, 16, high - 16 * CopycatSlopeLayerBlock.STEEP_GRADIENT, high);
                else
                    CopycatSlopeModelCore.assembleRunEdge(context, transform, kink, 16, 0, high);
            }
        } else {
            boolean lowRun = enhanced && (context.variant() & CopycatSlopeLayerBlock.RUN_LOW) != 0;
            boolean highRun = enhanced && (context.variant() & CopycatSlopeLayerBlock.RUN_HIGH) != 0;
            CopycatSlopeModelCore.assembleSlope(context, transform, low, high, enhanced, lowRun, highRun);
            if (lowRun) CopycatSlopeModelCore.assembleRunEdge(context, transform, 0, 8, low, (low + high) / 2);
            if (highRun) CopycatSlopeModelCore.assembleRunEdge(context, transform, 8, 16, (low + high) / 2, high);
        }
    }
}
