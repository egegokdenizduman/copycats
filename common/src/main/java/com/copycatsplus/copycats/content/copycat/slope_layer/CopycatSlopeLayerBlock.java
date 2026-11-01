package com.copycatsplus.copycats.content.copycat.slope_layer;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.CCShapes;
import com.copycatsplus.copycats.Copycats;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeBlock;
import com.copycatsplus.copycats.foundation.copycat.CCWaterloggedCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.IStateType;
import com.copycatsplus.copycats.utility.BlockUtils;
import com.copycatsplus.copycats.utility.InteractionUtils;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.placement.PoleHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.core.Direction.UP;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CopycatSlopeLayerBlock extends CCWaterloggedCopycatBlock implements SpecialBlockItemRequirement, IStateType {


    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
    // 9 and 10 fill the shallow run between 1 and 7, 11 to 14 the steep run between 3 and 5, stacking never reaches them
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 14);
    // with IN_WALL: the ridge runs along Y, FACING is the high edge and HALF picks the wall beside it
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;

    private static final String VERTICAL_ITEM_TAG = "VerticalSlopeLayer";

    // the shallow and steep runs from their low end up, each piece starts where the last one ends
    private static final List<List<RunPiece>> RUNS = List.of(
            List.of(new RunPiece(1, 0, 0), new RunPiece(9, 1, 0), new RunPiece(10, 2, 0), new RunPiece(7, 3, 0)),
            List.of(new RunPiece(3, 0, 0), new RunPiece(11, 1, 0), new RunPiece(12, 1, 1), new RunPiece(13, 2, 1), new RunPiece(14, 2, 2), new RunPiece(5, 3, 2))
    );
    // the rise of the steep run per pixel along it
    private static final double STEEP_GRADIENT = 0.75;

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public CopycatSlopeLayerBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(IN_WALL, false)
                .setValue(VERTICAL, false)
                .setValue(LAYERS, 1)
                .setValue(SHAPE, StairsShape.STRAIGHT)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(FACING, HALF, IN_WALL, VERTICAL, LAYERS, SHAPE));
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionUtils.sequentialItem(
                () -> usePlacementAssist(stack, state, level, pos, player, hand, hitResult),
                () -> super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        );
    }

    private static ItemInteractionResult usePlacementAssist(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        RunPlacement run = getClickedPlacement(state, level, pos, hitResult, player, stack);
        // the helper consumes every click, so stacking clicks must skip it
        if (run == null && canStack(state, hitResult.getDirection()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int count = stack.getCount();
        ItemInteractionResult result = InteractionUtils.usePlacementHelper(placementHelperId, stack, state, level, pos, player, hand, hitResult);
        // the helper only takes one item, a piece along the ridge copies the clicked one
        if (stack.getCount() < count)
            stack.shrink(getItemCost(run != null ? run.layers() : state.getValue(LAYERS)) - 1);
        return result;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = super.getStateForPlacement(context);
        if (stateForPlacement == null) return null;
        BlockPos blockPos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(blockPos);
        if (state.is(this)) {
            if (state.getValue(LAYERS) < 8)
                return withCornerShape(state.cycle(LAYERS), context.getLevel(), blockPos);
            else {
                Copycats.LOGGER.warn("Can't figure out where to place a slope layer! Please file an issue if you see this.");
                return state;
            }
        } else if (isVerticalItem(context.getItemInHand())) {
            return withCornerShape(continueRun(getVerticalStateForPlacement(stateForPlacement, context), context), context.getLevel(), blockPos);
        } else {
            return withCornerShape(continueRun(getRegularStateForPlacement(stateForPlacement, context), context), context.getLevel(), blockPos);
        }
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState,
                                  LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pNeighborPos) {
        BlockState state = super.updateShape(pState, pDirection, pNeighborState, pLevel, pCurrentPos, pNeighborPos);
        return pDirection.getAxis().isHorizontal() ? withCornerShape(state, pLevel, pCurrentPos) : state;
    }

    private static BlockState withCornerShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(SHAPE, getCornerShape(state, level, pos));
    }

    /**
     * Same rules as {@link CopycatSlopeBlock}, only connecting to floor or ceiling pieces whose surfaces meet.
     */
    private static StairsShape getCornerShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(IN_WALL) || state.getValue(LAYERS) == 8 || getKink(state.getValue(LAYERS)) != 0) return StairsShape.STRAIGHT;
        Direction facing = state.getValue(FACING);
        BlockState front = level.getBlockState(pos.relative(facing));
        if (connectsTo(state, front)) {
            Direction frontFacing = front.getValue(FACING);
            if (frontFacing.getAxis() != facing.getAxis() && canTakeShape(state, level, pos, frontFacing.getOpposite())) {
                return frontFacing == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }
        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        if (connectsTo(state, back)) {
            Direction backFacing = back.getValue(FACING);
            if (backFacing.getAxis() != facing.getAxis() && canTakeShape(state, level, pos, backFacing)) {
                return backFacing == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean connectsTo(BlockState state, BlockState other) {
        return other.getBlock() instanceof CopycatSlopeLayerBlock
                && !other.getValue(IN_WALL)
                && other.getValue(HALF) == state.getValue(HALF)
                && other.getValue(LAYERS).equals(state.getValue(LAYERS));
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockState other = level.getBlockState(pos.relative(side));
        return !connectsTo(state, other) || other.getValue(FACING) != state.getValue(FACING);
    }

    public static boolean isCorner(BlockState state) {
        return !state.getValue(IN_WALL) && state.getValue(SHAPE) != StairsShape.STRAIGHT;
    }

    private static BlockState getRegularStateForPlacement(BlockState state, BlockPlaceContext context) {
        Direction playerHorizontal = context.getHorizontalDirection();
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.UP || clickedFace == Direction.DOWN) {
            Half half = clickedFace == Direction.DOWN
                    ? Half.TOP
                    : Half.BOTTOM;
            return state
                    .setValue(FACING, playerHorizontal)
                    .setValue(IN_WALL, false)
                    .setValue(HALF, half);
        }
        else {
            Half half = context.getClickLocation().y - context.getClickedPos().getY() > 0.5
                    ? Half.TOP
                    : Half.BOTTOM;
            return state
                    .setValue(FACING, clickedFace)
                    .setValue(IN_WALL, true)
                    .setValue(HALF, half);
        }
    }

    // where a piece on the same base has its next run piece, the new piece joins that run, also diagonally
    private BlockState continueRun(BlockState placed, BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || context.isSecondaryUseActive()) return placed;
        BlockPos pos = context.getClickedPos();
        for (BlockPos neighborPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState neighbor = context.getLevel().getBlockState(neighborPos);
            if (!neighbor.is(this) || getBase(neighbor) != getBase(placed)) continue;
            for (Direction side : Direction.values()) {
                RunPlacement run = getRunPlacement(neighbor, context.getLevel(), neighborPos, side);
                if (run != null && run.pos().equals(pos) && canAfford(player, context.getItemInHand(), run.layers()))
                    return copyOrientation(placed, neighbor).setValue(LAYERS, run.layers());
            }
        }
        return placed;
    }

    // the piece that continues this one's run on the given side, null if there is none or its spot is taken
    private static RunPlacement getRunPlacement(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        int layers = state.getValue(LAYERS);
        for (List<RunPiece> run : RUNS) {
            for (RunPiece piece : run) {
                if (piece.layers() == layers) return getRunPlacement(state, level, pos, side, run, piece);
            }
        }
        return null;
    }

    private static RunPlacement getRunPlacement(BlockState state, BlockGetter level, BlockPos pos, Direction side, List<RunPiece> run, RunPiece piece) {
        Direction rise = getRise(state);
        Direction up = getBase(state).getOpposite();
        if (side != rise && side != rise.getOpposite() && side != up) return null;
        int step = side == rise.getOpposite() ? -1 : 1;
        for (int i = run.indexOf(piece) + step; i >= 0 && i < run.size(); i += step) {
            RunPiece next = run.get(i);
            boolean sameColumn = next.column() == piece.column();
            // the high side passes the wedge on top, the top only reaches that wedge
            if (side == rise && sameColumn) continue;
            if (side == up && !sameColumn) return null;
            BlockPos nextPos = pos.relative(rise, next.column() - piece.column()).relative(up, next.level() - piece.level());
            if (level.getBlockState(nextPos).canBeReplaced()) return new RunPlacement(nextPos, next.layers(), state.getValue(FACING));
            // going down, a wedge may already have its partner below
            if (!sameColumn || side == up) return null;
        }
        return null;
    }

    // the run piece a click on this piece places, null if the click isn't meant for the run
    private static RunPlacement getClickedRun(BlockState state, Level level, BlockPos pos, BlockHitResult ray, Player player, ItemStack held) {
        Direction side = ray.getDirection();
        int layers = state.getValue(LAYERS);
        // a wedge has no wall at its low edge, and the top of its partner is partly slope
        boolean noRealSide = getKink(layers) != 0 && side == (getLowEdge(layers) == 0 ? getRise(state).getOpposite() : getBase(state).getOpposite());
        // the stepped slope surface also reports the low side, only the real block side counts
        double depth = ray.getLocation().get(side.getAxis()) - pos.get(side.getAxis());
        if (!noRealSide && Math.abs(depth - (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) > 1e-4) return null;
        RunPlacement run = getRunPlacement(state, level, pos, side);
        return run != null && canAfford(player, held, run.layers()) ? run : null;
    }

    // the run comes first, then floor and ceiling pieces go beside the clicked one at its own height
    private static RunPlacement getClickedPlacement(BlockState state, Level level, BlockPos pos, BlockHitResult ray, Player player, ItemStack held) {
        RunPlacement run = getClickedRun(state, level, pos, ray, player, held);
        if (run != null || state.getValue(IN_WALL)) return run;
        int layers = state.getValue(LAYERS);
        CopycatSlopeBlock.SidePlacement side = CopycatSlopeBlock.getSidePlacement(state.getValue(FACING), state.getValue(HALF), getLowEdge(layers) == 0, pos, ray,
                end -> getKink(layers) == 0 && canTakeShape(state, level, pos, end));
        if (side == null || !level.getBlockState(side.pos()).canBeReplaced() || !canAfford(player, held, layers)) return null;
        return new RunPlacement(side.pos(), layers, side.facing());
    }

    private static boolean canAfford(Player player, ItemStack held, int layers) {
        return player.isCreative() || held.getCount() >= getItemCost(layers);
    }

    private static BlockState getVerticalStateForPlacement(BlockState state, BlockPlaceContext context) {
        boolean clickedWall = context.getClickedFace().getAxis().isHorizontal();
        Direction main = clickedWall ? context.getClickedFace().getOpposite() : context.getHorizontalDirection();
        Direction.Axis crossAxis = main.getClockWise().getAxis();
        double click = context.getClickLocation().get(crossAxis) - context.getClickedPos().get(crossAxis);
        Direction cross = Direction.fromAxisAndDirection(crossAxis, click > 0.5 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        return clickedWall ? withOrientation(state, main, cross) : withOrientation(state, cross, main);
    }

    /**
     * Marks a slope layer item so that it places the vertical form.
     */
    public static void markVerticalItem(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(VERTICAL_ITEM_TAG, true));
    }

    public static boolean isVerticalItem(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(VERTICAL_ITEM_TAG);
    }

    public static boolean isVertical(BlockState state) {
        return state.getValue(IN_WALL) && state.getValue(VERTICAL);
    }

    // the side the flat underside rests against
    private static Direction getBase(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean top = state.getValue(HALF) == Half.TOP;
        if (isVertical(state)) return top ? facing.getClockWise() : facing.getCounterClockWise();
        if (state.getValue(IN_WALL)) return facing.getOpposite();
        return top ? Direction.UP : Direction.DOWN;
    }

    // the direction of the high edge
    private static Direction getRise(BlockState state) {
        if (!state.getValue(IN_WALL) || isVertical(state)) return state.getValue(FACING);
        return state.getValue(HALF) == Half.TOP ? Direction.UP : Direction.DOWN;
    }

    private static BlockState copyOrientation(BlockState state, BlockState source) {
        return state.setValue(FACING, source.getValue(FACING))
                .setValue(HALF, source.getValue(HALF))
                .setValue(IN_WALL, source.getValue(IN_WALL))
                .setValue(VERTICAL, source.getValue(VERTICAL));
    }

    private static BlockState withOrientation(BlockState state, Direction base, Direction rise) {
        if (base.getAxis().isVertical())
            return state.setValue(IN_WALL, false).setValue(VERTICAL, false)
                    .setValue(FACING, rise)
                    .setValue(HALF, base == Direction.UP ? Half.TOP : Half.BOTTOM);
        if (rise.getAxis().isVertical())
            return state.setValue(IN_WALL, true).setValue(VERTICAL, false).setValue(SHAPE, StairsShape.STRAIGHT)
                    .setValue(FACING, base.getOpposite())
                    .setValue(HALF, rise == Direction.UP ? Half.TOP : Half.BOTTOM);
        return state.setValue(IN_WALL, true).setValue(VERTICAL, true).setValue(SHAPE, StairsShape.STRAIGHT)
                .setValue(FACING, rise)
                .setValue(HALF, base == rise.getClockWise() ? Half.TOP : Half.BOTTOM);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean canBeReplaced(@NotNull BlockState pState, BlockPlaceContext pUseContext) {
        ItemStack itemstack = pUseContext.getItemInHand();
        if (!itemstack.is(this.asItem())) return false;
        return canStack(pState, pUseContext.getClickedFace());
    }

    private static boolean canStack(BlockState state, Direction clickedFace) {
        if (state.getValue(LAYERS) >= 8) return false;
        if (clickedFace == getBase(state).getOpposite() || clickedFace == getRise(state).getOpposite()) return true;
        if (!isCorner(state)) return false;
        // a corner's second arm rises to one side, its surface steps face the other
        StairsShape shape = state.getValue(SHAPE);
        Direction facing = state.getValue(FACING);
        boolean left = shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;
        return clickedFace == (left ? facing.getClockWise() : facing.getCounterClockWise());
    }

    /**
     * How many items a piece with this many layers is worth.
     */
    public static int getItemCost(int layers) {
        return switch (layers) {
            case 9 -> 2;
            case 10 -> 3;
            case 11 -> 7;
            case 12 -> 2;
            case 13 -> 6;
            case 14 -> 1;
            default -> layers;
        };
    }

    // edge heights in quarters of a block
    public static int getLowEdge(int layers) {
        return switch (layers) {
            case 9 -> 1;
            case 10 -> 2;
            case 11 -> 3;
            case 12, 14 -> 0;
            case 13 -> 2;
            default -> Math.max(layers - 4, 0);
        };
    }

    public static int getHighEdge(int layers) {
        return switch (layers) {
            case 9 -> 2;
            case 10 -> 3;
            case 11, 13 -> 4;
            case 12 -> 2;
            case 14 -> 1;
            default -> Math.min(layers, 4);
        };
    }

    // where a steep run piece leaves the block top or floor, 0 for pieces that are one slope
    public static double getKink(int layers) {
        return switch (layers) {
            case 11, 12 -> 16.0 / 3;
            case 13, 14 -> 32.0 / 3;
            default -> 0;
        };
    }

    // surface height in pixels at z pixels from the low edge
    public static double getSurfaceHeight(int layers, double z) {
        double low = getLowEdge(layers) * 4;
        double high = getHighEdge(layers) * 4;
        if (getKink(layers) == 0) return low + z / 16 * (high - low);
        return low > 0 ? Math.min(high, low + z * STEEP_GRADIENT) : Math.max(0, high - (16 - z) * STEEP_GRADIENT);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        // run pieces come off whole, they can't drop to a lower layer
        if (state.getValue(LAYERS) <= 1 || state.getValue(LAYERS) > 8)
            return super.onSneakWrenched(state, context);

        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (world instanceof ServerLevel serverLevel) {
            if (player != null && !player.isCreative()) {
                // Respect loot tables
                List<ItemStack> drops = Block.getDrops(state.setValue(LAYERS, 1), serverLevel, pos, world.getBlockEntity(pos), player, context.getItemInHand());
                for (ItemStack drop : drops) {
                    player.getInventory().placeItemBackInInventory(drop);
                }
            }
            BlockPos up = pos.relative(Direction.UP);
            // need to call updateShape before setBlock to schedule a tick for water
            BlockState lowered = state.setValue(LAYERS, state.getValue(LAYERS) - 1).updateShape(Direction.UP, world.getBlockState(up), world, pos, up);
            world.setBlockAndUpdate(pos, withCornerShape(lowered, world, pos));
            IWrenchable.playRemoveSound(world, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
        return ICopycatBlock.getRequiredItemsForLayer(state, getItemCost(state.getValue(LAYERS)));
    }

    private static boolean isOccluded(BlockState state, BlockState other, Direction pDirection) {
        state = state.setValue(WATERLOGGED, false);
        other = other.setValue(WATERLOGGED, false);
        Direction facing = state.getValue(FACING);
        if (facing.getOpposite() == other.getValue(FACING) && pDirection == facing) return true;
        if (other.getValue(FACING) != facing) return false;
        return pDirection.getAxis() != facing.getAxis();
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        StairsShape shape = state.getValue(SHAPE);
        if (transform.mirror != null && transform.mirror != Mirror.NONE) {
            shape = CopycatSlopeBlock.mirrorShape(shape);
        }
        if (transform.rotationAxis != null && transform.rotationAxis.isHorizontal() && transform.rotation != Rotation.NONE) {
            // a half turn around a horizontal axis swaps the sides, a quarter turn has no matching corner
            shape = transform.rotation == Rotation.CLOCKWISE_180 ? CopycatSlopeBlock.mirrorShape(shape) : StairsShape.STRAIGHT;
        }
        BlockState transformed = withOrientation(state, BlockUtils.transformFacing(transform, getBase(state)), BlockUtils.transformFacing(transform, getRise(state)));
        return transformed.getValue(IN_WALL) ? transformed : transformed.setValue(SHAPE, shape);
    }

    @Override
    public boolean isPathfindable(@NotNull BlockState pState, @NotNull PathComputationType pType) {
        return switch (pType) {
            case LAND -> pState.getValue(LAYERS) < 5 && pState.getValue(FACING).equals(UP);
            default -> false;
        };
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getShape(BlockState pState, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        if (isVertical(pState))
            return CCShapes.VERTICAL_SLOPE_LAYER.get(pState.getValue(FACING)).get(pState.getValue(HALF)).get(pState.getValue(LAYERS)).toShape();
        if (isCorner(pState))
            return CCShapes.SLOPE_LAYER_CORNER.get(pState.getValue(FACING)).get(pState.getValue(HALF)).get(pState.getValue(SHAPE)).get(pState.getValue(LAYERS)).toShape();
        return CCShapes.SLOPE_LAYER.get(pState.getValue(FACING)).get(pState.getValue(HALF)).get(pState.getValue(IN_WALL)).get(pState.getValue(LAYERS)).toShape();
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
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LAYERS) == 8 ? 0.2f : 1.0f;
    }

    // column along the rise, level above the base
    private record RunPiece(int layers, int column, int level) {
    }

    private record RunPlacement(BlockPos pos, int layers, Direction facing) {
    }

    @MethodsReturnNonnullByDefault
    private static class PlacementHelper extends PoleHelper<Direction> {

        private PlacementHelper() {
            // runs along the ridge
            super(CCBlocks.COPYCAT_SLOPE_LAYER::has, state -> isVertical(state) ? Direction.Axis.Y : state.getValue(FACING).getClockWise().getAxis(), FACING);
        }

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return i -> i.getItem() instanceof BlockItem
                    && (((BlockItem) i.getItem()).getBlock() instanceof CopycatSlopeLayerBlock);
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            ItemStack held = getItemPredicate().test(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
            RunPlacement run = getClickedPlacement(state, world, pos, ray, player, held);
            if (run != null)
                return PlacementOffset.success(run.pos(), s -> withCornerShape(copyOrientation(s, state).setValue(FACING, run.facing()).setValue(LAYERS, run.layers()), world, run.pos()));
            if (canStack(state, ray.getDirection()))
                return PlacementOffset.fail();
            int layers = state.getValue(LAYERS);
            if (!canAfford(player, held, layers))
                return PlacementOffset.fail();
            PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
            BlockPos newPos = offset.getBlockPos();
            return offset.withTransform(s -> withCornerShape(copyOrientation(s, state).setValue(LAYERS, layers), world, newPos));
        }

    }
}
