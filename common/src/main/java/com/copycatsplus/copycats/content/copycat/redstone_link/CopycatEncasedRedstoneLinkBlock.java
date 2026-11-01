package com.copycatsplus.copycats.content.copycat.redstone_link;

import com.copycatsplus.copycats.CCBlockEntityTypes;
import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.utility.InteractionUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * A Create redstone link inside a copycat panel. It is only made by using a copycat panel on a link, so it has no item.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CopycatEncasedRedstoneLinkBlock extends RedstoneLinkBlock implements ICopycatBlock, SpecialBlockItemRequirement {

    public CopycatEncasedRedstoneLinkBlock(Properties properties) {
        super(properties);
    }

    /**
     * Encases the link at the position if the stack is a copycat panel.
     *
     * @return Whether the interaction was handled.
     */
    public static boolean tryEncase(Level level, BlockPos pos, Player player, ItemStack stack) {
        BlockState link = level.getBlockState(pos);
        if (!AllBlocks.REDSTONE_LINK.has(link) || !AllBlocks.COPYCAT_PANEL.isIn(stack))
            return false;
        if (player.isShiftKeyDown() || !player.mayBuild())
            return false;
        if (level.isClientSide)
            return true;

        BlockState encased = CCBlocks.COPYCAT_ENCASED_REDSTONE_LINK.get().withPropertiesOf(link);
        swapKeepingData(level, pos, encased);
        level.playSound(null, pos, encased.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1, .75f);
        if (!player.isCreative())
            stack.shrink(1);
        return true;
    }

    // Frequencies and signal carry over, since both blocks read the same block entity data
    private static void swapKeepingData(Level level, BlockPos pos, BlockState newState) {
        if (!(level.getBlockEntity(pos) instanceof RedstoneLinkBlockEntity oldBE))
            return;
        CompoundTag data = oldBE.saveWithoutMetadata(level.registryAccess());
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        if (!(level.getBlockEntity(pos) instanceof RedstoneLinkBlockEntity newBE))
            return;
        newBE.loadWithComponents(data, level.registryAccess());
        newBE.updateSelfAndAttached(newState);
    }

    @Override
    public boolean canToggleCT(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        ICopycatBlockEntity copycatBE = getCopycatBlockEntity(level, pos);
        if (copycatBE == null)
            return InteractionResult.PASS;
        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(copycatBE.getConsumedItem());
            player.getInventory().placeItemBackInInventory(AllBlocks.COPYCAT_PANEL.asStack());
        }
        copycatBE.setConsumedItem(ItemStack.EMPTY);
        swapKeepingData(level, pos, AllBlocks.REDSTONE_LINK.get().withPropertiesOf(state));
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return onWrenched(state, context);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionUtils.sequentialItem(
                () -> ICopycatBlock.super.useItemOn(stack, state, level, pos, player, hand, hitResult),
                () -> super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        );
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        ICopycatBlock.super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving, super::onRemove);
    }

    @Override
    public BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        super.playerWillDestroy(level, pos, state, player);
        ICopycatBlock.super.playerWillDestroy(level, pos, state, player);
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return AllShapes.CASING_3PX.get(state.getValue(FACING));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return AllBlocks.REDSTONE_LINK.asStack();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME,
                List.of(AllBlocks.REDSTONE_LINK.asStack(), AllBlocks.COPYCAT_PANEL.asStack()));
    }

    @Override
    public BlockEntityType<? extends RedstoneLinkBlockEntity> getBlockEntityType() {
        return CCBlockEntityTypes.COPYCAT_ENCASED_REDSTONE_LINK.get();
    }

    @Override
    public @NotNull BlockState mirror(@NotNull BlockState pState, @NotNull Mirror pMirror) {
        return super.mirror(pState, pMirror);
    }

    @Override
    public @NotNull BlockState rotate(@NotNull BlockState pState, Rotation pRot) {
        return super.rotate(pState, pRot);
    }
}
