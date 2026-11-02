package com.copycatsplus.copycats.utility;

import com.copycatsplus.copycats.content.copycat.vertical_stairs.CopycatVerticalStairBlock;
import com.copycatsplus.copycats.foundation.copycat.CopycatExternalContext;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.model.ScaledBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.mixin.copycat.VoxelShapeAccessor;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static com.copycatsplus.copycats.utility.MathUtils.*;

public class BlockFaceUtils {

    private static final ThreadLocal<Map<BiFunction<VoxelShape, VoxelShape, Boolean>, Object2ByteLinkedOpenHashMap<ShapeKey>>> SHAPE_OP_CACHE = ThreadLocal.withInitial(HashMap::new);

    // the outermost pixel of the block on each side, indexed by direction
    private static final VoxelShape[] FACE_BANDS = {
            Shapes.box(0, 0, 0, 1, 1 / 16.0, 1),
            Shapes.box(0, 15 / 16.0, 0, 1, 1, 1),
            Shapes.box(0, 0, 0, 1, 1, 1 / 16.0),
            Shapes.box(0, 0, 15 / 16.0, 1, 1, 1),
            Shapes.box(0, 0, 0, 1 / 16.0, 1, 1),
            Shapes.box(15 / 16.0, 0, 0, 1, 1, 1)
    };

    // the width of a casing's border in the stair models
    private static final double BORDER = 2 / 16.0;
    private static final double EDGE_EPSILON = 1e-5;

    public static record ShapeKey(VoxelShape fromShape, VoxelShape toShape) {
    }

    // Copycats use noOcclusion, so vanilla keeps no face shapes for them and each lookup made new shapes,
    // which SHAPE_OP_CACHE never matched. Their shapes depend on the state only.
    private static final Map<BlockState, StateShapes> STATE_SHAPES = new ConcurrentHashMap<>();

    private record StateShapes(VoxelShape[] faces, VoxelShape[] trimmedFaces, VoxelShape[] edges) {
    }

    private static boolean hasStateShapes(BlockState state) {
        return state.getBlock() instanceof ICopycatBlock && !(state.getBlock() instanceof IMultiStateCopycatBlock);
    }

    private static StateShapes getStateShapes(BlockGetter level, BlockState state, BlockPos pos) {
        // get first: computeIfAbsent locks the bin, and all meshing threads ask for the same few states
        StateShapes shapes = STATE_SHAPES.get(state);
        if (shapes != null) return shapes;
        VoxelShape[] faces = new VoxelShape[6];
        VoxelShape[] trimmedFaces = new VoxelShape[6];
        VoxelShape[] edges = new VoxelShape[36];
        for (Direction side : Direction.values()) {
            faces[side.ordinal()] = state.getFaceOcclusionShape(level, pos, side);
            trimmedFaces[side.ordinal()] = Shapes.join(faces[side.ordinal()], Shapes.block(), BooleanOp.AND);
        }
        for (Direction side : Direction.values()) {
            for (Direction textureFace : Direction.values()) {
                edges[side.ordinal() * 6 + textureFace.ordinal()] = Shapes.join(faces[side.ordinal()], FACE_BANDS[textureFace.ordinal()], BooleanOp.AND);
            }
        }
        shapes = new StateShapes(faces, trimmedFaces, edges);
        StateShapes existing = STATE_SHAPES.putIfAbsent(state, shapes);
        return existing != null ? existing : shapes;
    }

    private static VoxelShape getFaceShape(BlockGetter level, BlockState state, BlockPos pos, Direction face) {
        if (hasStateShapes(state))
            return getStateShapes(level, state, pos).faces()[face.ordinal()];
        return state.getFaceOcclusionShape(level, pos, face);
    }

    private static boolean invokeCachedOperation(BiFunction<VoxelShape, VoxelShape, Boolean> operation, VoxelShape fromShape, VoxelShape toShape) {
        Object2ByteLinkedOpenHashMap<ShapeKey> cache = SHAPE_OP_CACHE.get().computeIfAbsent(operation, op -> {
            Object2ByteLinkedOpenHashMap<ShapeKey> cacheMap = new Object2ByteLinkedOpenHashMap<>(2048, 0.25f) {
                @Override
                protected void rehash(int i) {
                }
            };
            cacheMap.defaultReturnValue((byte) 127);
            return cacheMap;
        });
        ShapeKey key = new ShapeKey(fromShape, toShape);
        byte result = cache.getAndMoveToFirst(key);
        if (result == 127) {
            boolean value = operation.apply(fromShape, toShape);
            if (cache.size() >= 2048) cache.removeLastByte();
            cache.putAndMoveToFirst(key, (byte) (value ? 1 : 0));
            return value;
        } else {
            return result == 1;
        }
    }

    private static boolean processBlockFace(BlockGetter level,
                                            BlockState fromState,
                                            BlockPos fromPos,
                                            BlockState toState,
                                            BlockPos toPos,
                                            Direction fromFace,
                                            BiFunction<VoxelShape, VoxelShape, Boolean> operation) {
        VoxelShape fromShape = null;
        Vec3i scale = new Vec3i(1, 1, 1);
        Vec3i inner = new Vec3i(0, 0, 0);
        BlockPos truePos = fromPos;
        if (level instanceof ScaledBlockAndTintGetter scaledWorld) {
            scale = scaledWorld.getScale();
            truePos = scaledWorld.getTruePos(fromPos);
            if (fromState.getBlock() instanceof IMultiStateCopycatBlock copycatBlock &&
                    copycatBlock.vectorScale(fromState).equals(scaledWorld.getScale())) {
                String property = scaledWorld.getPropertyForRender(fromState, fromPos);
                if (!copycatBlock.partExists(fromState, property))
                    return false;
                inner = copycatBlock.getVectorFromProperty(fromState, property);
                fromShape = copycatBlock.getPartialFaceShape(scaledWorld, fromState, property, fromFace);
            }
        }
        if (fromShape == null) {
            fromShape = getFaceShape(level, fromState, fromPos, fromFace);
        }
        if (fromShape.isEmpty()) {
            return false;
        }

        VoxelShape toShape = null;
        List<String> potentialParts = null;
        if (toState.getBlock() instanceof IMultiStateCopycatBlock copycatBlock) {
            Vec3i toScale = copycatBlock.vectorScale(toState);
            Axis connectingAxis = fromFace.getAxis();
            BlockGetter world = level;
            BlockPos toTruePos = toPos;
            String fallbackProperty = copycatBlock.defaultProperty();
            if (level instanceof ScaledBlockAndTintGetter scaledWorld) {
                world = scaledWorld.getWrapped();
                toTruePos = scaledWorld.getTruePos(toPos);
                fallbackProperty = scaledWorld.getRenderingProperty();
                if (toTruePos.equals(truePos)) {
                    potentialParts = List.of(scaledWorld.getPropertyForRender(toState, toPos));
                }
            }
            if (potentialParts == null) {
                if (replaceAxis(scale, connectingAxis, 0).equals(replaceAxis(toScale, connectingAxis, 0))) {
                    potentialParts = List.of(copycatBlock.getPropertyFromRender(fallbackProperty, toState, world, replaceAxis(inner, connectingAxis,
                            fromFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : (toScale.get(connectingAxis) - 1)
                    ), toTruePos));
                } else {
                    potentialParts = new ArrayList<>(4);
                    int connectingPart = fromFace.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : (toScale.get(connectingAxis) - 1);
                    Axis axis1 = switch (connectingAxis) {
                        case X -> Axis.Y;
                        case Y -> Axis.Z;
                        case Z -> Axis.X;
                    };
                    Axis axis2 = switch (connectingAxis) {
                        case X -> Axis.Z;
                        case Y -> Axis.X;
                        case Z -> Axis.Y;
                    };
                    for (int i = 0; i < toScale.get(axis1); i++) {
                        for (int j = 0; j < toScale.get(axis2); j++) {
                            potentialParts.add(copycatBlock.getPropertyFromRender(fallbackProperty, toState, world, replaceAxis(replaceAxis(new Vec3i(connectingPart, connectingPart, connectingPart), axis1, i), axis2, j), toTruePos));
                        }
                    }
                }
            }
            for (String partProp : potentialParts) {
                toShape = copycatBlock.getPartialFaceShape(world, toState, partProp, fromFace.getOpposite());
                if (invokeCachedOperation(operation, fromShape, toShape)) {
                    String property = CopycatExternalContext.getPropertyForAppearance();
                    if (property == null) property = copycatBlock.defaultProperty();
                    CopycatExternalContext.setPropertyForAppearance(copycatBlock.getPropertyFromRender(property, toState, world, copycatBlock.getVectorFromProperty(toState, partProp), toTruePos));
                    return true;
                }
            }
        }
        if (toShape == null) {
            toShape = getFaceShape(level, toState, toPos, fromFace.getOpposite());
        }
        CopycatExternalContext.setPropertyForAppearance(null);
        return invokeCachedOperation(operation, fromShape, toShape);
    }

    /**
     * Compare the occlusion shape of two blocks to determine if the occluding block completely covers the occluded block, which allows occlusion.
     */
    public static boolean canOcclude(BlockGetter level,
                                     BlockState occludedState,
                                     BlockPos occludedPos,
                                     BlockState occludingState,
                                     BlockPos occludingPos,
                                     Direction occludedFace) {
        return processBlockFace(level,
                occludedState,
                occludedPos,
                occludingState,
                occludingPos,
                occludedFace,
                (occluded, occluding) -> !Shapes.joinIsNotEmpty(occluded, occluding, BooleanOp.ONLY_FIRST));
    }

    /**
     * Compare the occlusion shape of two blocks to determine if their faces match.
     */
    public static boolean faceMatch(BlockGetter level,
                                    BlockState fromState,
                                    BlockPos fromPos,
                                    BlockState toState,
                                    BlockPos toPos,
                                    Direction fromFace) {
        return processBlockFace(level,
                fromState,
                fromPos,
                toState,
                toPos,
                fromFace,
                (from, to) -> !Shapes.joinIsNotEmpty(from, to, BooleanOp.NOT_SAME));
    }

    /**
     * Whether a textured face lies flush across two adjacent copycats, meeting along the same edge even if the rest of
     * their touching faces differ. Multi-state copycats are skipped because their parts render separately.
     */
    public static boolean edgeMatch(BlockGetter level,
                                    BlockState fromState,
                                    BlockPos fromPos,
                                    BlockState toState,
                                    BlockPos toPos,
                                    Direction fromFace,
                                    Direction textureFace) {
        if (textureFace.getAxis() == fromFace.getAxis()) return false;
        if (!canCompareEdges(fromState, toState)) return false;
        VoxelShape fromEdge = getEdge(level, fromState, fromPos, fromFace, textureFace);
        if (fromEdge.isEmpty()) return false;
        VoxelShape toEdge = getEdge(level, toState, toPos, fromFace.getOpposite(), textureFace);
        if (CopycatExternalContext.shouldConnectPartialEdges())
            return Shapes.joinIsNotEmpty(fromEdge, toEdge, BooleanOp.AND);
        return !Shapes.joinIsNotEmpty(fromEdge, toEdge, BooleanOp.NOT_SAME);
    }

    /**
     * Find where the second connected texture result of a copycat replaces the normal one: on each flush face that
     * partly meets an adjacent copycat of the same material, the half of the face beside that seam, along the length of
     * the seam. A connection only changes the texture on the side that it connects to, so the rest of the face keeps
     * its normal border.
     *
     * @return The regions in block space for each textured face. Empty if the copycat has no partial seams.
     */
    public static Map<Direction, List<AABB>> getPartialEdgeRegions(BlockGetter level, BlockPos pos, BlockState state, BlockState material) {
        Map<Direction, List<AABB>> regions = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            BlockPos neighbourPos = pos.relative(side);
            BlockState neighbour = level.getBlockState(neighbourPos);
            if (!canCompareEdges(state, neighbour) || !ICopycatBlock.getMaterial(level, neighbourPos).is(material.getBlock()))
                continue;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == side.getAxis()) continue;
                VoxelShape edge = getEdge(level, state, pos, side, face);
                VoxelShape neighbourEdge = getEdge(level, neighbour, neighbourPos, side.getOpposite(), face);
                boolean notched = isNotchedEdge(level, state, pos, side, face);
                if (!notched && !Shapes.joinIsNotEmpty(edge, neighbourEdge, BooleanOp.NOT_SAME)) continue;
                VoxelShape overlap = Shapes.join(edge, neighbourEdge, BooleanOp.AND);
                if (overlap.isEmpty()) continue;
                Axis along = remainingAxis(side.getAxis(), face.getAxis());
                // Where the edge carries on past the neighbour, the border stays long enough to meet the neighbour's border
                double min = overlap.min(along) > edge.min(along) + EDGE_EPSILON ? overlap.min(along) + BORDER : overlap.min(along);
                double max = overlap.max(along) < edge.max(along) - EDGE_EPSILON ? overlap.max(along) - BORDER : overlap.max(along);
                if (max - min < EDGE_EPSILON) continue;
                regions.computeIfAbsent(face, f -> new ArrayList<>())
                        .add(getSeamRegion(side, face, along, min, max, notched ? 0.5 - BORDER : 0.5));
            }
        }
        return regions;
    }

    /**
     * Whether a side of a stair's L shaped face is shorter than the face. The inner edges of the L are cut from the same
     * border of the texture as that side, so it can only connect through {@link #getPartialEdgeRegions}, which leaves
     * those inner edges alone.
     */
    public static boolean isNotchedEdge(BlockGetter level, BlockState state, BlockPos pos, Direction side, Direction textureFace) {
        if (textureFace.getAxis() == side.getAxis() || !CopycatVerticalStairBlock.isStairs(state)) return false;
        VoxelShape edge = getEdge(level, state, pos, side, textureFace);
        if (edge.isEmpty()) return false;
        Axis along = remainingAxis(side.getAxis(), textureFace.getAxis());
        // a face's slice shape reports the bounds of the whole block, so measure the joined shape
        VoxelShape faceShape = hasStateShapes(state)
                ? getStateShapes(level, state, pos).trimmedFaces()[textureFace.ordinal()]
                : Shapes.join(state.getFaceOcclusionShape(level, pos, textureFace), Shapes.block(), BooleanOp.AND);
        return edge.min(along) > faceShape.min(along) + EDGE_EPSILON || edge.max(along) < faceShape.max(along) - EDGE_EPSILON;
    }

    private static boolean canCompareEdges(BlockState fromState, BlockState toState) {
        return fromState.getBlock() instanceof ICopycatBlock && toState.getBlock() instanceof ICopycatBlock
                && !(fromState.getBlock() instanceof IMultiStateCopycatBlock) && !(toState.getBlock() instanceof IMultiStateCopycatBlock);
    }

    private static VoxelShape getEdge(BlockGetter level, BlockState state, BlockPos pos, Direction side, Direction textureFace) {
        if (hasStateShapes(state))
            return getStateShapes(level, state, pos).edges()[side.ordinal() * 6 + textureFace.ordinal()];
        return Shapes.join(state.getFaceOcclusionShape(level, pos, side), FACE_BANDS[textureFace.ordinal()], BooleanOp.AND);
    }

    private static Axis remainingAxis(Axis first, Axis second) {
        for (Axis axis : Axis.values()) {
            if (axis != first && axis != second) return axis;
        }
        throw new IllegalArgumentException("Axes must differ");
    }

    private static AABB getSeamRegion(Direction side, Direction face, Axis along, double min, double max, double depth) {
        double[] lower = new double[3];
        double[] upper = new double[3];
        for (Axis axis : Axis.values()) {
            int i = axis.ordinal();
            if (axis == side.getAxis()) {
                lower[i] = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - depth : 0;
                upper[i] = lower[i] + depth;
            } else if (axis == face.getAxis()) {
                lower[i] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15 / 16.0 : 0;
                upper[i] = lower[i] + 1 / 16.0;
            } else {
                lower[i] = min;
                upper[i] = max;
            }
        }
        return new AABB(lower[0], lower[1], lower[2], upper[0], upper[1], upper[2]);
    }

    public static VoxelShape getPartialFaceShape(BlockGetter level, BlockState state, String property, Direction face) {
        IMultiStateCopycatBlock copycatBlock = (IMultiStateCopycatBlock) state.getBlock();
        Vec3i scale = copycatBlock.vectorScale(state);
        Vec3i part = copycatBlock.getVectorFromProperty(state, property);
        return getPartialFaceShape(state.getOcclusionShape(level, BlockPos.ZERO), face, part, scale);
    }

    public static VoxelShape getPartialFaceShape(VoxelShape voxelShape, Direction direction, Vec3i part, Vec3i scale) {
        int i;
        Axis axis = direction.getAxis();
        double startX = part.getX() / (double) scale.getX();
        double startY = part.getY() / (double) scale.getY();
        double startZ = part.getZ() / (double) scale.getZ();
        double sizeX = 1.0 / scale.getX();
        double sizeY = 1.0 / scale.getY();
        double sizeZ = 1.0 / scale.getZ();
        double endX = startX + sizeX;
        double endY = startY + sizeY;
        double endZ = startZ + sizeZ;
        VoxelShape bounds = Shapes.box(startX, startY, startZ, endX, endY, endZ);
        voxelShape = Shapes.joinUnoptimized(voxelShape, bounds, BooleanOp.AND);
        int axisSize = ((VoxelShapeAccessor) voxelShape).copycats$getShape().getSize(axis);
        boolean isEmpty = false;
        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            isEmpty = DoubleMath.fuzzyCompare(voxelShape.max(axis), switch (axis) {
                case X -> endX;
                case Y -> endY;
                case Z -> endZ;
            }, 1.0E-7) < 0;
            i = Mth.floor(Mth.clamp(axisSize * axis.choose(endX, endY, endZ), -1, axisSize)) - 1;
        } else {
            isEmpty = DoubleMath.fuzzyCompare(voxelShape.min(axis), switch (axis) {
                case X -> startX;
                case Y -> startY;
                case Z -> startZ;
            }, 1.0E-7) > 0;
            i = Mth.floor(Mth.clamp(axisSize * axis.choose(startX, startY, startZ), -1, axisSize));
        }
        if (isEmpty) {
            return Shapes.empty();
        }
        return new SliceShape(voxelShape, axis, i);
    }
}
