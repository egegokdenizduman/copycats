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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public CopycatSlopeLayerBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(IN_WALL, false)
                .setValue(LAYERS, 1)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(FACING, HALF, IN_WALL, LAYERS));
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

    @SuppressWarnings("deprecation")
    @Override
    public boolean canBeReplaced(@NotNull BlockState pState, BlockPlaceContext pUseContext) {
        ItemStack itemstack = pUseContext.getItemInHand();
        if (!itemstack.is(this.asItem())) return false;
        return canStack(pState, pUseContext.getClickedFace());
    }

    private static boolean canStack(BlockState state, Direction clickedFace) {
        if (state.getValue(LAYERS) == 8) return false;
        Half half = state.getValue(HALF);
        boolean inWall = state.getValue(IN_WALL);
        Direction facing = state.getValue(FACING);
        if(!inWall) {
            if (clickedFace == facing.getOpposite()) return true;
        }
        else {
            if (clickedFace == facing) return true;
        }
        if (half == Half.TOP && clickedFace == Direction.DOWN) return true;
        if (half == Half.BOTTOM && clickedFace == Direction.UP) return true;
        return false;
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
        // todo: vertical slope layer not supported yet
        if (transform.rotationAxis != null && transform.rotationAxis != Direction.Axis.Y && (transform.rotation == Rotation.CLOCKWISE_90 || transform.rotation == Rotation.COUNTERCLOCKWISE_90))
            transform.rotation = Rotation.NONE;
        return BlockUtils.transformStepLikeHorizontal(state, transform, defaultBlockState());
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
            // floor and IN_WALL forms both run along the axis perpendicular to FACING
            super(CCBlocks.COPYCAT_SLOPE_LAYER::has, state -> state.getValue(FACING).getClockWise().getAxis(), FACING);
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
                            .setValue(IN_WALL, state.getValue(IN_WALL)));
        }

    }
}
