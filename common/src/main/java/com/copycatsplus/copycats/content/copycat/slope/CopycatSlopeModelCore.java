package com.copycatsplus.copycats.content.copycat.slope;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.AssemblyTransform;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableAABB;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableUV;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVec3;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableVertex;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadTransform;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static com.copycatsplus.copycats.foundation.copycat.model.assembly.CopycatRenderContext.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.MutableCullFace.*;
import static com.copycatsplus.copycats.foundation.copycat.model.assembly.quad.QuadSlope.map;

public class CopycatSlopeModelCore extends CopycatModelCore {

    // Width of the edge band, matching the 2 pixel border of framed textures like casings
    private static final double ENHANCED_MARGIN = 2;
    private static final double DIAGONAL_EPSILON = 1.0E-5;
    private static final double RAMP_EDGE_OFFSET = 0.002;

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
            case STRAIGHT -> {
                assembleSlope(context, transform, 0, 16, enhanced);
                if (enhanced && (context.variant() & CopycatSlopeBlock.RAMP_LOW) != 0)
                    assembleRampEdge(context, transform, 0);
                if (enhanced && (context.variant() & CopycatSlopeBlock.RAMP_HIGH) != 0)
                    assembleRampEdge(context, transform, 8);
                if (enhanced && (context.variant() & CopycatSlopeBlock.RAMP_LOW_JOINT) != 0)
                    assembleRampJoint(context, transform, true);
                if (enhanced && (context.variant() & CopycatSlopeBlock.RAMP_HIGH_JOINT) != 0)
                    assembleRampJoint(context, transform, false);
            }
            case INNER_LEFT, INNER_RIGHT -> assembleCorner(context, transform, true, 0, 16, enhanced);
            case OUTER_LEFT, OUTER_RIGHT -> assembleCorner(context, transform, false, 0, 16, enhanced);
        }
    }

    /**
     * Draws half of the surface's edge on the side faces without the mitre that ends it inside the block, so that it
     * meets the next piece of a ramp. At the low edge it reaches past the block, in front of the corner of the block below.
     */
    private static void assembleRampEdge(CopycatRenderContext context, AssemblyTransform transform, double startZ) {
        context.assemblePiece(
                transform,
                vec3(0, 16 - ENHANCED_MARGIN, startZ),
                aabb(16, ENHANCED_MARGIN, 8).move(0, 16 - ENHANCED_MARGIN, startZ),
                cull(UP | DOWN | NORTH | SOUTH),
                shearRampEdge((vertex, depth) -> {})
        );
    }

    /**
     * Draws half of the edge up to the border it meets where a slope of the other half stands against this one: the
     * wall past the low tip, or the bottom row past the high tip. The two are mitred, so their rows line up. The rows
     * at the block's end could be its corner tile, so the piece comes from the middle of the edge and stretches there.
     */
    private static void assembleRampJoint(CopycatRenderContext context, AssemblyTransform transform, boolean low) {
        final double startZ = low ? ENHANCED_MARGIN : 8;
        final double innerZ = (low ? ENHANCED_MARGIN : 16 - ENHANCED_MARGIN) / 16;
        final double endZ = low ? 0 : 1;
        // how far along the block the mitre goes per unit of depth
        final double slant = low ? 1 : Math.sqrt(2) - 1;
        context.assemblePiece(
                transform,
                vec3(0, 16 - ENHANCED_MARGIN, startZ),
                aabb(16, ENHANCED_MARGIN, 8 - ENHANCED_MARGIN).move(0, 16 - ENHANCED_MARGIN, startZ),
                cull(UP | DOWN | NORTH | SOUTH),
                shearRampEdge((vertex, depth) -> {
                    if (Math.abs(vertex.xyz.z - innerZ) < DIAGONAL_EPSILON) vertex.xyz.z = endZ + depth * slant;
                })
        );
    }

    private static QuadTransform shearRampEdge(BiConsumer<MutableVertex, Double> place) {
        return (quad, sprite) -> {
            for (MutableVertex vertex : quad.vertices) {
                double depth = 1 - vertex.xyz.y;
                place.accept(vertex, depth);
                // the edge is cut square to a 45 degree surface, so it is this much taller than its rows
                vertex.xyz.y = vertex.xyz.z - depth * Math.sqrt(2);
                vertex.xyz.x += vertex.xyz.x < 0.5 ? -RAMP_EDGE_OFFSET : RAMP_EDGE_OFFSET;
            }
            quad.disableFinalAutoCull = true;
            return true;
        };
    }

    /**
     * Assembles a corner of two slopes meeting along the x = z diagonal, rising along max(x, z) for an inner corner and
     * min(x, z) for an outer one. Each half is the straight slope of that arm, the second one mirrored across the
     * diagonal, so every edge shared with a straight neighbour matches it.
     */
    public static void assembleCorner(CopycatRenderContext context, AssemblyTransform transform, boolean inner, double minHeight, double maxHeight, boolean enhanced) {
        AssemblyTransform mirror = t -> t.rotateY(270).flipZ(true);
        for (boolean secondPart : new boolean[]{false, true}) {
            // the mirror swaps x and z, so both arms keep the same side in their own space
            CopycatRenderContext clipped = new ClippedContext(context, clipToDiagonal(!inner, secondPart));
            assembleSlope(clipped, transform, minHeight, maxHeight, enhanced);
            assembleSlope(clipped, mirror.andThen(transform), minHeight, maxHeight, enhanced);
        }
    }

    /**
     * Cuts the quad along the x = z diagonal, keeping one side. New corners take their position and UV from the edge
     * they cut. A cut can leave five corners, more than a quad holds, so secondPart gives the quad for the last ones.
     */
    private static QuadTransform clipToDiagonal(boolean keepHighX, boolean secondPart) {
        return (quad, sprite) -> {
            List<MutableVertex> kept = new ArrayList<>();
            boolean inside = false;
            for (int i = 0; i < 4; i++) {
                MutableVertex from = quad.vertices.get(i);
                MutableVertex to = quad.vertices.get((i + 1) % 4);
                double fromSide = diagonalSide(from, keepHighX);
                double toSide = diagonalSide(to, keepHighX);
                inside |= fromSide > 0;
                if (fromSide >= 0) kept.add(from);
                if (fromSide * toSide < 0) kept.add(lerp(from, to, fromSide / (fromSide - toSide)));
            }
            if (!inside || (secondPart && kept.size() < 5)) return false;
            if (secondPart) kept = List.of(kept.get(0), kept.get(3), kept.get(4));
            // a triangle repeats its last corner, as a separate vertex because transforms visit each one
            List<MutableVertex> vertices = new ArrayList<>(kept.subList(0, Math.min(kept.size(), 4)));
            while (vertices.size() < 4) vertices.add(lerp(vertices.get(2), vertices.get(2), 0));
            quad.vertices = vertices;
            return true;
        };
    }

    private static double diagonalSide(MutableVertex vertex, boolean keepHighX) {
        double side = keepHighX ? vertex.xyz.x - vertex.xyz.z : vertex.xyz.z - vertex.xyz.x;
        return Math.abs(side) < DIAGONAL_EPSILON ? 0 : side;
    }

    private static MutableVertex lerp(MutableVertex from, MutableVertex to, double amount) {
        MutableVec3 xyz = from.xyz.copy().scale(1 - amount).add(to.xyz.copy().scale(amount));
        float u = from.uv.u + (to.uv.u - from.uv.u) * (float) amount;
        float v = from.uv.v + (to.uv.v - from.uv.v) * (float) amount;
        return new MutableVertex(xyz, new MutableUV(u, v));
    }

    /**
     * Runs a transform on every quad after the piece's own transforms.
     */
    private record ClippedContext(CopycatRenderContext context, QuadTransform clip) implements CopycatRenderContext {

        private QuadTransform[] withClip(QuadTransform... transforms) {
            QuadTransform[] all = Arrays.copyOf(transforms, transforms.length + 1);
            all[transforms.length] = clip;
            return all;
        }

        @Override
        public void assemblePiece(AssemblyTransform assemblyTransform, MutableVec3 offset, MutableAABB select, MutableCullFace cull) {
            context.assemblePiece(assemblyTransform, offset, select, cull, clip);
        }

        @Override
        public void assemblePiece(AssemblyTransform assemblyTransform, MutableVec3 offset, MutableAABB select, MutableCullFace cull, QuadTransform... transforms) {
            context.assemblePiece(assemblyTransform, offset, select, cull, withClip(transforms));
        }

        @Override
        public void assembleAll() {
            context.assembleAll();
        }

        @Override
        public void assembleRaw(AABB crop, Vec3 move) {
            context.assembleRaw(crop, move, clip);
        }

        @Override
        public void assembleRaw(AABB crop, Vec3 move, QuadTransform... transforms) {
            context.assembleRaw(crop, move, withClip(transforms));
        }
    }

    /**
     * Assembles a slope that only covers part of the block. From minHeight 0 it rises from the kink to the high edge,
     * with nothing before the kink. Otherwise it rises from the low edge to the kink and stays flat at maxHeight.
     */
    public static void assembleKinkedSlope(CopycatRenderContext context, AssemblyTransform transform, double minHeight, double maxHeight, double kink) {
        if (minHeight == 0) {
            context.assemblePiece(
                    transform,
                    vec3(0, 0, kink),
                    aabb(16, 16, 16 - kink).move(0, 0, kink),
                    cull(NORTH),
                    updateUV(slope(Direction.UP, (a, b) -> map(kink, 16, 0, maxHeight, b)))
            );
            return;
        }
        context.assemblePiece(
                transform,
                vec3(0, 0, 0),
                aabb(16, 16, kink),
                cull(SOUTH),
                updateUV(slope(Direction.UP, (a, b) -> map(0, kink, minHeight, maxHeight, b)))
        );
        context.assemblePiece(
                transform,
                vec3(0, 0, kink),
                aabb(16, 16, 16 - kink).move(0, 0, kink),
                cull(NORTH)
        );
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
