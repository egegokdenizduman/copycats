package com.copycatsplus.copycats.content.copycat.slope_layer;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.CCShapes;
import com.copycatsplus.copycats.Copycats;
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
import net.minecraft.world.level.block.Block;
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
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    // with IN_WALL: the ridge runs along Y, FACING is the high edge and HALF picks the wall beside it
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

    private static final String VERTICAL_ITEM_TAG = "VerticalSlopeLayer";

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public CopycatSlopeLayerBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(IN_WALL, false)
                .setValue(VERTICAL, false)
                .setValue(LAYERS, 1)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(FACING, HALF, IN_WALL, VERTICAL, LAYERS));
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionUtils.sequentialItem(
                // the helper consumes every click, so stacking clicks must skip it
                () -> canStack(state, hitResult.getDirection())
                        ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                        : InteractionUtils.usePlacementHelper(placementHelperId, stack, state, level, pos, player, hand, hitResult),
                () -> super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = super.getStateForPlacement(context);
        if (stateForPlacement == null) return null;
        BlockPos blockPos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(blockPos);
        if (state.is(this)) {
            if (state.getValue(LAYERS) < 8)
                return state.cycle(LAYERS);
            else {
                Copycats.LOGGER.warn("Can't figure out where to place a slope layer! Please file an issue if you see this.");
                return state;
            }
        } else if (isVerticalItem(context.getItemInHand())) {
            return getVerticalStateForPlacement(stateForPlacement, context);
        } else {
            Direction playerHorizontal = context.getHorizontalDirection();
            Direction clickedFace = context.getClickedFace();
            if (clickedFace == Direction.UP || clickedFace == Direction.DOWN) {
                Half half = clickedFace == Direction.DOWN
                        ? Half.TOP
                        : Half.BOTTOM;
                return stateForPlacement
                        .setValue(FACING, playerHorizontal)
                        .setValue(IN_WALL, false)
                        .setValue(HALF, half);
            }
            else {
                Half half = context.getClickLocation().y - context.getClickedPos().getY() > 0.5
                        ? Half.TOP
                        : Half.BOTTOM;
                return stateForPlacement
                        .setValue(FACING, clickedFace)
                        .setValue(IN_WALL, true)
                        .setValue(HALF, half);
            }
        }
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

    private static BlockState withOrientation(BlockState state, Direction base, Direction rise) {
        if (base.getAxis().isVertical())
            return state.setValue(IN_WALL, false).setValue(VERTICAL, false)
                    .setValue(FACING, rise)
                    .setValue(HALF, base == Direction.UP ? Half.TOP : Half.BOTTOM);
        if (rise.getAxis().isVertical())
            return state.setValue(IN_WALL, true).setValue(VERTICAL, false)
                    .setValue(FACING, base.getOpposite())
                    .setValue(HALF, rise == Direction.UP ? Half.TOP : Half.BOTTOM);
        return state.setValue(IN_WALL, true).setValue(VERTICAL, true)
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
        if (state.getValue(LAYERS) == 8) return false;
        return clickedFace == getBase(state).getOpposite() || clickedFace == getRise(state).getOpposite();
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (state.getValue(LAYERS) <= 1)
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
            world.setBlockAndUpdate(pos, state.setValue(LAYERS, state.getValue(LAYERS) - 1).updateShape(Direction.UP, world.getBlockState(up), world, pos, up));
            IWrenchable.playRemoveSound(world, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
        return ICopycatBlock.getRequiredItemsForLayer(state, LAYERS);
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
        return withOrientation(state, BlockUtils.transformFacing(transform, getBase(state)), BlockUtils.transformFacing(transform, getRise(state)));
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
            if (canStack(state, ray.getDirection()))
                return PlacementOffset.fail();
            // one item buys one layer, so LAYERS stays at its default
            return super.getOffset(player, world, state, pos, ray)
                    .withTransform(s -> s.setValue(FACING, state.getValue(FACING))
                            .setValue(HALF, state.getValue(HALF))
                            .setValue(IN_WALL, state.getValue(IN_WALL))
                            .setValue(VERTICAL, state.getValue(VERTICAL)));
        }

    }
}
