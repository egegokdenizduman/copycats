package com.copycatsplus.copycats.content.copycat.slope;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVec3;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVertex;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadTransform;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope.map;

public class CopycatSlopeModelCore extends CopycatModelCore {

    // Width of the edge band, matching the 2 pixel border of framed textures like casings
    private static final double ENHANCED_MARGIN = 2;

    @Override
    public void emitCopycatQuads(String key, BlockState state, CopycatRenderContext context, BlockState material) {
        Direction facing = state.getValue(CopycatSlopeBlock.FACING);
        Half half = state.getValue(CopycatSlopeBlock.HALF);
        int rot = (int) facing.toYRot();
        boolean flipY = half == Half.TOP;
        StairsShape shape = state.getValue(CopycatSlopeBlock.SHAPE);
        boolean flipX = shape == StairsShape.INNER_RIGHT || shape == StairsShape.OUTER_RIGHT;
        AssemblyTransform transform = t -> t.flipX(flipX).flipY(flipY).rotateY(rot);
        switch (shape) {
            case STRAIGHT -> assembleSlope(context, transform, 0, 16, enhanced);
            case INNER_LEFT, INNER_RIGHT -> assembleCorner(context, transform, true, 0, 16);
            case OUTER_LEFT, OUTER_RIGHT -> assembleCorner(context, transform, false, 0, 16);
        }
    }

    /**
     * Assembles a corner of two slopes meeting along the x = z diagonal. The surface rises from minHeight to maxHeight
     * along max(x, z) for an inner corner and min(x, z) for an outer one, so each half of the surface is a flat triangle.
     */
    public static void assembleCorner(CopycatRenderContext context, AssemblyTransform transform, boolean inner, double minHeight, double maxHeight) {
        QuadSlope.QuadSlopeFunction height = (x, z) -> minHeight + (inner ? Math.max(x, z) : Math.min(x, z)) * (maxHeight - minHeight) / 16;
        // the low walls of an outer corner have no height unless it starts above the floor
        int emptyFaces = !inner && minHeight == 0 ? NORTH | WEST : 0;
        context.assemblePiece(
                transform,
                vec3(0, 0, 0),
                aabb(16, 16, 16),
                cull(UP | emptyFaces),
                updateUV(slope(Direction.UP, height))
        );
        for (boolean highX : new boolean[]{true, false}) {
            context.assemblePiece(
                    transform,
                    vec3(0, 0, 0),
                    aabb(16, 16, 16),
                    cull(DOWN | NORTH | SOUTH | EAST | WEST),
                    updateUV(clipToDiagonal(highX), slope(Direction.UP, height))
            );
        }
    }

    /**
     * Moves vertices on the wrong side of the x = z diagonal onto it, leaving the part of the quad on the kept side.
     */
    private static QuadTransform clipToDiagonal(boolean keepHighX) {
        return (quad, sprite) -> {
            for (MutableVertex vertex : quad.vertices) {
                MutableVec3 xyz = vertex.xyz;
                if (keepHighX ? xyz.x < xyz.z : xyz.x > xyz.z) {
                    xyz.x = xyz.z = (xyz.x + xyz.z) / 2;
                }
            }
            return true;
        };
    }

    public static void assembleSlope(CopycatRenderContext context, AssemblyTransform transform, double minHeight, double maxHeight, boolean enhanced) {
        if (minHeight == 0) {
            if (enhanced) {
                assembleTriangularSlope(context, transform, maxHeight, ENHANCED_MARGIN);
            } else {
                assembleTriangularSlope(context, transform, maxHeight);
            }
        } else {
            if (enhanced) {
                assembleTrapezoidSlope(context, transform, minHeight, maxHeight, ENHANCED_MARGIN);
            } else {
                assembleTrapezoidSlope(context, transform, minHeight, maxHeight);
            }
        }
    }

    public static void assembleTriangularSlope(CopycatRenderContext context, AssemblyTransform transform, double maxHeight) {
        context.assemblePiece(
                transform,
                vec3(0, 0, 0),
                aabb(16, 16, 16),
                cull(NORTH),
                updateUV(slope(Direction.UP, (a, b) -> map(0, 16, 0, maxHeight, b)))
        );
    }

    public static void assembleTrapezoidSlope(CopycatRenderContext context, AssemblyTransform transform, double minHeight, double maxHeight) {
        context.assemblePiece(
                transform,
                vec3(0, 0, 0),
                aabb(16, 16, 16),
                cull(0),
                updateUV(slope(Direction.UP, (a, b) -> map(0, 16, minHeight, maxHeight, b)))
        );
    }

    public static void assembleTriangularSlope(CopycatRenderContext context, AssemblyTransform transform, double maxHeight, double margin) {
        final double angleBottom = Math.atan2(maxHeight, 16);
        final double angleTop = Math.atan2(16, maxHeight);
        final double marginAdjTop = margin / Math.tan(angleTop / 2);
        final double length = Math.sqrt(maxHeight * maxHeight + 16 * 16);

        // On very shallow slopes the margin doesn't fit above the floor, so the surface piece runs into the floor
        final boolean fits = margin / Math.tan(angleBottom / 2) + marginAdjTop <= length;
        final double marginAdjBottom = margin / Math.tan(fits ? angleBottom / 2 : angleBottom);
        final double coreZ = fits ? marginAdjBottom : margin / Math.sin(angleBottom);
        final double coreY = fits ? margin : 0;

        context.assemblePiece(
                transform,
                vec3(0, 0, 0),
                aabb(16, 16, coreZ),
                cull(UP | NORTH | SOUTH),
                updateUV(slope(Direction.UP, (a, b) -> map(0, coreZ, 0, coreY, b)))
        );
        context.assemblePiece(
                transform,
                vec3(0, 0, coreZ),
                aabb(16, 16, 16 - margin - coreZ).move(0, 0, coreZ),
                cull(UP | NORTH | SOUTH),
                updateUV(slope(Direction.UP, (a, b) -> map(coreZ, 16 - margin, coreY, maxHeight - marginAdjTop, b)))
        );
        if (maxHeight == 16) {
            context.assemblePiece(
                    transform,
                    vec3(0, 0, 16 - margin),
                    aabb(16, 16, margin).move(0, 0, 16 - margin),
                    cull(UP | NORTH),
                    updateUV(slope(Direction.UP, (a, b) -> map(16 - margin, 16, maxHeight - marginAdjTop, maxHeight, b)))
            );
        } else {
            assembleHighWall(context, transform, maxHeight, margin, marginAdjTop);
        }
        assembleSurface(context, transform, 0, maxHeight, margin, marginAdjBottom, marginAdjTop);
    }

    public static void assembleTrapezoidSlope(CopycatRenderContext context, AssemblyTransform transform, double minHeight, double maxHeight, double margin) {
        final double angleBottom = Math.atan2(maxHeight - minHeight, 16) + Math.PI / 2;
        final double marginAdjBottom = margin / Math.tan(angleBottom / 2);
        final double angleTop = Math.atan2(16, maxHeight - minHeight);
        final double marginAdjTop = margin / Math.tan(angleTop / 2);

        if (minHeight == 16 || minHeight == 0) {
            context.assemblePiece(
                    transform,
                    vec3(0, 0, 0),
                    aabb(16, 16, margin),
                    cull(UP | SOUTH),
                    updateUV(slope(Direction.UP, (a, b) -> map(0, margin, minHeight, minHeight - marginAdjBottom, b)))
            );
        } else {
            context.assemblePiece(
                    transform,
                    vec3(0, 0, 0),
                    aabb(16, minHeight / 2, margin).move(0, 0, 0),
                    cull(UP | SOUTH)
            );
            context.assemblePiece(
                    transform,
                    vec3(0, 0, 0),
                    aabb(16, minHeight / 2, margin).move(0, 16 - minHeight / 2, 0),
                    cull(UP | DOWN | SOUTH),
                    scale(
                            pivot(0, 0, 0),
                            scale(1, 32 / minHeight, 1)
                    ),
                    updateUV(slope(Direction.UP, (a, b) -> map(0, margin, 16, 16 - marginAdjBottom / minHeight * 32, b))),
                    scale(
                            pivot(0, 0, 0),
                            scale(1, minHeight / 32, 1)
                    ),
                    translate(0, minHeight / 2, 0)
            );
        }
        context.assemblePiece(
                transform,
                vec3(0, 0, margin),
                aabb(16, 16, 16 - margin * 2).move(0, 0, margin),
                cull(UP | NORTH | SOUTH),
                updateUV(slope(Direction.UP, (a, b) -> map(margin, 16 - margin, minHeight - marginAdjBottom, maxHeight - marginAdjTop, b)))
        );
        if (maxHeight == 16 || maxHeight == 0) {
            context.assemblePiece(
                    transform,
                    vec3(0, 0, 16 - margin),
                    aabb(16, 16, margin).move(0, 0, 16 - margin),
                    cull(UP | NORTH),
                    updateUV(slope(Direction.UP, (a, b) -> map(16 - margin, 16, maxHeight - marginAdjTop, maxHeight, b)))
            );
        } else {
            assembleHighWall(context, transform, maxHeight, margin, marginAdjTop);
        }
        assembleSurface(context, transform, minHeight, maxHeight, margin, marginAdjBottom, marginAdjTop);
    }

    /**
     * Assembles the wall at the high edge from the bottom and top rows of the material, so that both borders show.
     * The cut under the surface can reach below the middle of the wall, so the split stays under it on a whole pixel.
     */
    private static void assembleHighWall(CopycatRenderContext context, AssemblyTransform transform, double maxHeight, double margin, double marginAdjTop) {
        final double split = Math.min(maxHeight / 2, Math.floor(maxHeight - marginAdjTop));
        final double topRows = maxHeight - split;
        context.assemblePiece(
                transform,
                vec3(0, 0, 16 - margin),
                aabb(16, split, margin).move(0, 0, 16 - margin),
                cull(UP | NORTH)
        );
        context.assemblePiece(
                transform,
                vec3(0, 16 - topRows, 16 - margin),
                aabb(16, topRows, margin).move(0, 16 - topRows, 16 - margin),
                cull(UP | DOWN | NORTH),
                scale(
                        pivot(16, 16, 16),
                        scale(1, 16 / topRows, 1)
                ),
                updateUV(slope(Direction.UP, (a, b) -> map(16 - margin, 16, 16 - marginAdjTop / topRows * 16, 16, b))),
                scale(
                        pivot(16, 16, 16),
                        scale(1, topRows / 16, 1)
                ),
                translate(0, -16 + maxHeight, 0)
        );
    }

    /**
     * Assembles the sloped surface as a board cut from the top of the material. Its length becomes a whole number of
     * equal texture pixels. The low half runs up from the first row and the high half back from the last row, so both
     * borders stay whole, the halves meet on the pixel grid, and pieces of the same angle share one layout.
     */
    private static void assembleSurface(CopycatRenderContext context, AssemblyTransform transform, double minHeight, double maxHeight, double margin, double endLow, double endHigh) {
        final double slopeAngle = Math.toDegrees(Math.atan2(maxHeight - minHeight, 16));
        final double length = Math.sqrt((maxHeight - minHeight) * (maxHeight - minHeight) + 16 * 16);

        final int pixels = (int) Math.round(length);
        final double pixelSize = length / pixels;
        final int lowHalfEnd = (pixels + 1) / 2;
        final int highHalfStart = lowHalfEnd - (pixels - 16);
        final double endRowLow = endLow / pixelSize;
        final double endRowHigh = 16 - endHigh / pixelSize;

        final QuadTransform placeLow = placeOnSurface(minHeight, 0, slopeAngle, pixelSize);
        final QuadTransform placeHigh = placeOnSurface(maxHeight, 16, slopeAngle, pixelSize);

        context.assemblePiece(
                transform,
                vec3(0, 0, 0),
                aabb(16, 16, endRowLow),
                cull(DOWN | NORTH | SOUTH),
                updateUV(slope(Direction.DOWN, (a, b) -> map(0, endRowLow, 0, margin, b))),
                placeLow
        );
        context.assemblePiece(
                transform,
                vec3(0, 16 - margin, endRowLow),
                aabb(16, margin, lowHalfEnd - endRowLow).move(0, 16 - margin, endRowLow),
                cull(DOWN | NORTH | SOUTH),
                placeLow
        );
        context.assemblePiece(
                transform,
                vec3(0, 16 - margin, highHalfStart),
                aabb(16, margin, endRowHigh - highHalfStart).move(0, 16 - margin, highHalfStart),
                cull(DOWN | NORTH | SOUTH),
                placeHigh
        );
        context.assemblePiece(
                transform,
                vec3(0, 0, endRowHigh),
                aabb(16, 16, 16 - endRowHigh).move(0, 0, endRowHigh),
                cull(DOWN | NORTH | SOUTH),
                updateUV(slope(Direction.DOWN, (a, b) -> map(endRowHigh, 16, margin, 0, b))),
                placeHigh
        );
    }

    /**
     * Moves a piece of the material's top face onto the slope, keeping texture row z at height y.
     */
    private static QuadTransform placeOnSurface(double y, double z, double slopeAngle, double pixelSize) {
        final QuadTransform lower = translate(0, y - 16, 0);
        final QuadTransform stretch = scale(pivot(0, y, z), scale(1, 1, pixelSize));
        final QuadTransform tilt = rotate(pivot(0, y, z), angle(-slopeAngle, 0, 0));
        return (quad, sprite) -> {
            lower.transformQuad(quad, sprite);
            stretch.transformQuad(quad, sprite);
            tilt.transformQuad(quad, sprite);
            return true;
        };
    }
}
