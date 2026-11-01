package com.copycatsplus.copycats.content.copycat.redstone_link;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace.*;

/**
 * A 3 pixel panel of the material, like Create's copycat panel. The middle pixel of each side has a 4 pixel gap for the
 * insert, which comes from the block's own model.
 */
public class CopycatEncasedRedstoneLinkModelCore extends CopycatModelCore {

    @Override
    public void registerModels(List<ModelEntry> entries) {
        super.registerModels(entries);
        entries.add(SUPER);
    }

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatEncasedRedstoneLinkBlock.FACING);
        boolean horizontal = facing.getAxis().isHorizontal();
        int xRot = horizontal ? 90 : facing == Direction.DOWN ? 180 : 0;
        int yRot = horizontal ? (int) facing.toYRot() + 180 : 0;
        AssemblyTransform transform = t -> t.rotateX(xRot).rotateY(yRot);

        context.assemblePiece(transform,
                vec3(0, 0, 0),
                aabb(16, 1, 16),
                cull(UP));
        context.assemblePiece(transform,
                vec3(0, 2, 0),
                aabb(16, 1, 16).move(0, 15, 0),
                cull(DOWN));

        // Corners of the middle pixel, leaving the insert gaps between them
        for (int x : new int[]{0, 10}) {
            for (int z : new int[]{0, 10}) {
                context.assemblePiece(transform,
                        vec3(x, 1, z),
                        aabb(6, 1, 6).move(x, 14, z),
                        cull(UP | DOWN | (x == 0 ? EAST : WEST) | (z == 0 ? SOUTH : NORTH)));
            }
        }
    }
}
