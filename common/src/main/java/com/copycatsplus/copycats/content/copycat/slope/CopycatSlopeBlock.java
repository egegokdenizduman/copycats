package com.copycatsplus.copycats.content.copycat.slope;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.CCShapes;
import com.copycatsplus.copycats.foundation.copycat.CCWaterloggedCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.CopycatExternalContext;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICustomCTBlocking;
import com.copycatsplus.copycats.foundation.copycat.IStateType;
import com.copycatsplus.copycats.utility.BlockUtils;
import com.copycatsplus.copycats.utility.InteractionUtils;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.placement.PoleHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.function.Predicate;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CopycatSlopeBlock extends CCWaterloggedCopycatBlock implements IStateType, ICustomCTBlocking {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;

    // a slope has almost no wall at its low edge, so this much of the surface counts as that side
    private static final double LOW_EDGE_DEPTH = 2.0 / 16;
    private static final double TURN_DEPTH = 4.0 / 16;
    private static final double ON_FACE = 1e-4;

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public CopycatSlopeBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(SHAPE, StairsShape.STRAIGHT)
        );
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionUtils.sequentialItem(
                () -> InteractionUtils.usePlacementHelper(placementHelperId, stack, state, level, pos, player, hand, hitResult),
                () -> super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        );
    }

    @Override
    public Optional<Boolean> isCTBlocked(BlockAndTintGetter reader, BlockState state, BlockPos pos, BlockPos connectingPos, BlockPos blockingPos, Direction face) {
        BlockState blocking = reader.getBlockState(blockingPos);
        if (blocking.is(this)) {
            // a slope over a side neighbour leaves the surface free, but one on the block behind the high edge covers it
            return Optional.of(isBehindHighEdge(state, pos, connectingPos, face) && blocking.getValue(HALF) == state.getValue(HALF));
        }
        return Optional.empty();
    }

    private static boolean isBehindHighEdge(BlockState state, BlockPos pos, BlockPos connectingPos, Direction face) {
        if (face != getSurfaceFace(state)) return false;
        BlockPos offset = connectingPos.subtract(pos);
        Direction side = Direction.fromDelta(offset.getX(), offset.getY(), offset.getZ());
        return side != null && isHighEdge(state, side);
    }

    @Override
    public boolean isCTContinued(BlockState state, BlockPos pos, BlockPos otherPos, Direction face) {
        // a side face narrows to nothing at the low edge, where a border would only show as a stray notch
        if (face.getAxis().isVertical() || isHighEdge(state, face) || isLowEdge(state, face)) return false;
        Direction rising = getRisingDirection(state, face);
        return rising != null && otherPos.subtract(pos).get(rising.getAxis()) == -rising.getAxisDirection().getStep();
    }

    @Nullable
    @Override
    public BlockPos getCTSurfacePos(BlockAndTintGetter reader, BlockState state, BlockPos pos, BlockPos otherPos, Direction face) {
        if (face != getSurfaceFace(state)) return null;
        BlockPos offset = otherPos.subtract(pos);
        int step = getSurfaceStep(state, offset.getX(), offset.getZ());
        if (step == 0) return null;
        BlockPos surfacePos = otherPos.relative(face, step);
        BlockState surface = reader.getBlockState(surfacePos);
        if (isSlopeOfSameHalf(state, surface) && getSurfaceStep(surface, -offset.getX(), -offset.getZ()) == -step)
            return surfacePos;
        // the two arms of a corner sit level with each other, either side of the crease
        BlockState other = reader.getBlockState(otherPos);
        boolean diagonal = offset.getX() != 0 && offset.getZ() != 0;
        return diagonal && isSlopeOfSameHalf(state, other) && getSurfaceStep(other, -offset.getX(), -offset.getZ()) == step ? otherPos : null;
    }

    @Override
    public boolean isIgnoredConnectivitySide(BlockAndTintGetter reader, BlockState fromState, Direction face, BlockPos fromPos, @Nullable BlockPos toPos, @Nullable BlockState toState) {
        // there is no wall at the low edge for a neighbour's face to run into
        if (!CopycatExternalContext.isForBlockingLogic() && isLowEdge(fromState, face)) return true;
        return super.isIgnoredConnectivitySide(reader, fromState, face, fromPos, toPos, toState);
    }

    private static Direction getSurfaceFace(BlockState state) {
        return state.getValue(HALF) == Half.TOP ? Direction.DOWN : Direction.UP;
    }

    private static boolean isHighEdge(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(SHAPE)) {
            case STRAIGHT -> side == facing;
            case INNER_LEFT -> side == facing || side == facing.getCounterClockWise();
            case INNER_RIGHT -> side == facing || side == facing.getClockWise();
            case OUTER_LEFT, OUTER_RIGHT -> false;
        };
    }

    private static boolean isLowEdge(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(SHAPE)) {
            case STRAIGHT -> side == facing.getOpposite();
            case INNER_LEFT, INNER_RIGHT -> false;
            case OUTER_LEFT -> side == facing.getOpposite() || side == facing.getClockWise();
            case OUTER_RIGHT -> side == facing.getOpposite() || side == facing.getCounterClockWise();
        };
    }

    // the direction a side face climbs in, as seen on that face
    @Nullable
    private static Direction getRisingDirection(BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis() != face.getAxis()) return facing;
        return switch (state.getValue(SHAPE)) {
            case STRAIGHT -> null;
            case INNER_LEFT, OUTER_LEFT -> facing.getCounterClockWise();
            case INNER_RIGHT, OUTER_RIGHT -> facing.getClockWise();
        };
    }

    /**
     * How many blocks along the surface face the surface has moved at this horizontal offset, where a ramp carries on
     * diagonally. 0 when it stays level there.
     */
    private static int getSurfaceStep(BlockState state, int x, int z) {
        Direction xSide = x == 0 ? null : x > 0 ? Direction.EAST : Direction.WEST;
        Direction zSide = z == 0 ? null : z > 0 ? Direction.SOUTH : Direction.NORTH;
        if (xSide != null && isHighEdge(state, xSide) || zSide != null && isHighEdge(state, zSide)) return 1;
        if (xSide != null && isLowEdge(state, xSide) || zSide != null && isLowEdge(state, zSide)) return -1;
        if (xSide == null || zSide == null) return 0;
        // straight along the crease of a corner
        if (isHighEdge(state, xSide.getOpposite()) && isHighEdge(state, zSide.getOpposite())) return -1;
        if (isLowEdge(state, xSide.getOpposite()) && isLowEdge(state, zSide.getOpposite())) return 1;
        return 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isPathfindable(@NotNull BlockState pState, @NotNull PathComputationType pType) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = super.getStateForPlacement(context);
        if (stateForPlacement == null) return null;
        Direction facing = context.getHorizontalDirection();
        Half half = context.getClickedFace() == Direction.DOWN
                ? Half.TOP
                : context.getClickedFace() == Direction.UP
                ? Half.BOTTOM
                : context.getClickLocation().y - context.getClickedPos().getY() > 0.5
                ? Half.TOP
                : Half.BOTTOM;
        stateForPlacement = stateForPlacement.setValue(FACING, facing).setValue(HALF, half);
        return stateForPlacement.setValue(SHAPE, getSlopeShape(stateForPlacement, context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState,
                                  LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pNeighborPos) {
        BlockState state = super.updateShape(pState, pDirection, pNeighborState, pLevel, pCurrentPos, pNeighborPos);
        return pDirection.getAxis().isHorizontal() ? state.setValue(SHAPE, getSlopeShape(state, pLevel, pCurrentPos)) : state;
    }

    /**
     * Same rules as {@link net.minecraft.world.level.block.StairBlock}, with slopes only connecting to slopes.
     */
    private static StairsShape getSlopeShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState front = level.getBlockState(pos.relative(facing));
        if (isSlopeOfSameHalf(state, front)) {
            Direction frontFacing = front.getValue(FACING);
            if (frontFacing.getAxis() != facing.getAxis() && canTakeShape(state, level, pos, frontFacing.getOpposite())) {
                return frontFacing == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }
        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        if (isSlopeOfSameHalf(state, back)) {
            Direction backFacing = back.getValue(FACING);
            if (backFacing.getAxis() != facing.getAxis() && canTakeShape(state, level, pos, backFacing)) {
                return backFacing == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        return StairsShape.STRAIGHT;
    }

    /**
     * The next piece for a click on a long side of a floor or ceiling slope, null for any other click.
     * Near a free row end it turns, so that the clicked piece becomes a corner.
     */
    @Nullable
    public static SidePlacement getSidePlacement(Direction facing, Half half, boolean thinLowEdge, BlockPos pos, BlockHitResult ray, Predicate<Direction> isFreeEnd) {
        Direction side = ray.getDirection();
        Direction low = facing.getOpposite();
        Direction surface = half == Half.TOP ? Direction.DOWN : Direction.UP;
        boolean highClick = side == facing && getDepth(facing, pos, ray) < ON_FACE;
        boolean lowClick = side == low && getDepth(low, pos, ray) < ON_FACE
                || thinLowEdge && side == surface && getDepth(low, pos, ray) < LOW_EDGE_DEPTH;
        if (!highClick && !lowClick) return null;
        BlockPos newPos = pos.relative(highClick ? facing : low);
        Direction end = facing.getClockWise();
        if (getDepth(end, pos, ray) >= TURN_DEPTH) end = end.getOpposite();
        if (getDepth(end, pos, ray) >= TURN_DEPTH || !isFreeEnd.test(end)) return new SidePlacement(newPos, facing);
        // the corner shape wants a piece behind that rises to the free end, or one in front that rises away from it
        return new SidePlacement(newPos, highClick ? end.getOpposite() : end);
    }

    // how far inside the block the click is, measured from the given side
    private static double getDepth(Direction side, BlockPos pos, BlockHitResult ray) {
        double hit = ray.getLocation().get(side.getAxis()) - pos.get(side.getAxis());
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - hit : hit;
    }

    private static boolean isSlopeOfSameHalf(BlockState state, BlockState other) {
        return other.getBlock() instanceof CopycatSlopeBlock && other.getValue(HALF) == state.getValue(HALF);
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockState other = level.getBlockState(pos.relative(side));
        return !isSlopeOfSameHalf(state, other) || other.getValue(FACING) != state.getValue(FACING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(FACING, HALF, SHAPE));
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getShape(BlockState pState, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        return CCShapes.SLOPE.get(pState.getValue(FACING)).get(pState.getValue(HALF)).get(pState.getValue(SHAPE)).toShape();
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getCollisionShape(BlockState pState, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        if (pState.getValue(HALF) == Half.TOP)
            return getShape(pState, pLevel, pPos, pContext);
        return CCShapes.SLOPE_COLLISION.get(pState.getValue(FACING)).get(pState.getValue(SHAPE)).toShape();
    }


    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }


    public boolean hidesNeighborFace(BlockGetter level,
                                     BlockPos pos,
                                     BlockState state,
                                     BlockState neighborState,
                                     Direction dir) {
        return ICopycatBlock.hidesNeighborFace(level, pos, state, neighborState, dir);
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        StairsShape shape = state.getValue(SHAPE);
        if (transform.mirror != null && transform.mirror != Mirror.NONE) {
            shape = mirrorShape(shape);
        }
        if (transform.rotationAxis != null && transform.rotationAxis.isHorizontal() && transform.rotation != Rotation.NONE) {
            // a half turn around a horizontal axis swaps the sides, a quarter turn has no matching corner
            shape = transform.rotation == Rotation.CLOCKWISE_180 ? mirrorShape(shape) : StairsShape.STRAIGHT;
        }
        BlockState transformed = BlockUtils.transformStepLikeHorizontal(state, transform, CCBlocks.COPYCAT_VERTICAL_SLOPE.getDefaultState());
        return transformed.hasProperty(SHAPE) ? transformed.setValue(SHAPE, shape) : transformed;
    }

    public static StairsShape mirrorShape(StairsShape shape) {
        return switch (shape) {
            case STRAIGHT -> StairsShape.STRAIGHT;
            case INNER_LEFT -> StairsShape.INNER_RIGHT;
            case INNER_RIGHT -> StairsShape.INNER_LEFT;
            case OUTER_LEFT -> StairsShape.OUTER_RIGHT;
            case OUTER_RIGHT -> StairsShape.OUTER_LEFT;
        };
    }

    public record SidePlacement(BlockPos pos, Direction facing) {
    }

    @MethodsReturnNonnullByDefault
    private static class PlacementHelper extends PoleHelper<Direction> {

        private PlacementHelper() {
            super(CCBlocks.COPYCAT_SLOPE::has, state -> state.getValue(FACING).getClockWise().getAxis(), FACING);
        }

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return i -> i.getItem() instanceof BlockItem
                    && (((BlockItem) i.getItem()).getBlock() instanceof CopycatSlopeBlock);
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            SidePlacement side = getSidePlacement(state.getValue(FACING), state.getValue(HALF), true, pos, ray, end -> canTakeShape(state, world, pos, end));
            if (side != null && world.getBlockState(side.pos()).canBeReplaced())
                return PlacementOffset.success(side.pos(), s -> getPlacedState(s, state, side.facing(), world, side.pos()));
            PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
            BlockPos newPos = offset.getBlockPos();
            return offset.withTransform(s -> getPlacedState(s, state, state.getValue(FACING), world, newPos));
        }

        // the helper places without getStateForPlacement, so the shape is worked out here
        private static BlockState getPlacedState(BlockState placed, BlockState clicked, Direction facing, Level world, BlockPos newPos) {
            placed = placed.setValue(FACING, facing).setValue(HALF, clicked.getValue(HALF));
            return placed.setValue(SHAPE, getSlopeShape(placed, world, newPos));
        }

    }
}
