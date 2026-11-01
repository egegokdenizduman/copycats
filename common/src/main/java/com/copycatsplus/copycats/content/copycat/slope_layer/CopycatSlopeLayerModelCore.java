package com.copycatsplus.copycats.content.copycat.slope_layer;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeModelCore;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

public class CopycatSlopeLayerModelCore extends CopycatModelCore {

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        int layer = state.getValue(CopycatSlopeLayerBlock.LAYERS);
        Direction facing = state.getValue(CopycatSlopeLayerBlock.FACING);
        Half half = state.getValue(CopycatSlopeLayerBlock.HALF);
        boolean in_wall = state.getValue(CopycatSlopeLayerBlock.IN_WALL);

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

        CopycatSlopeModelCore.assembleSlope(context, transform, CopycatSlopeLayerBlock.getLowEdge(layer) * 4, CopycatSlopeLayerBlock.getHighEdge(layer) * 4, enhanced);
    }
}
