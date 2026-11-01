package com.copycatsplus.copycats.content.copycat.slope;

import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Entity.collide climbs one riser per call, too few for the 1 pixel risers of a slope, and gravity
 * alone makes entities hop down it. So on slopes a move is split up, then pulled down onto the surface.
 */
public class SlopeWalking {
    private static final double MAX_STEP = 0.05;
    private static final double SNAP_MARGIN = 1 / 16.0;

    public static boolean applies(Entity entity, Vec3 movement) {
        if (!entity.onGround() || entity.maxUpStep() <= 0 || movement.y > 0 || movement.horizontalDistanceSqr() < 1.0E-7)
            return false;
        AABB box = entity.getBoundingBox().expandTowards(movement.x, 0, movement.z);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY - 1), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.minY), Mth.floor(box.maxZ))) {
            if (isWalkableSlope(entity.level().getBlockState(pos)))
                return true;
        }
        return false;
    }

    private static boolean isWalkableSlope(BlockState state) {
        if (state.getBlock() instanceof CopycatSlopeBlock)
            return state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
        return state.getBlock() instanceof CopycatSlopeLayerBlock
                && !state.getValue(CopycatSlopeLayerBlock.IN_WALL)
                && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }

    public static Vec3 move(Entity entity, Vec3 movement, UnaryOperator<Vec3> collide) {
        AABB start = entity.getBoundingBox();
        double distance = movement.horizontalDistance();
        int steps = Mth.ceil(distance / MAX_STEP);
        Vec3 step = movement.scale(1.0 / steps);
        Vec3 moved = Vec3.ZERO;
        try {
            for (int i = 0; i < steps; i++) {
                moved = moved.add(collide.apply(step));
                entity.setBoundingBox(start.move(moved));
            }
        } finally {
            entity.setBoundingBox(start);
        }

        double reach = distance + SNAP_MARGIN;
        double drop = Entity.collideBoundingBox(entity, new Vec3(0, -reach, 0), start.move(moved), entity.level(), List.of()).y;
        if (drop > -reach)
            return moved.add(0, drop, 0);
        // Entity.move reads any change to y as a landing
        return Mth.equal(moved.y, movement.y) ? new Vec3(moved.x, movement.y, moved.z) : moved;
    }
}
