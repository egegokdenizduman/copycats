package com.copycatsplus.copycats.content.copycat.slope_layer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class CopycatSlopeLayerItem extends BlockItem {

    public CopycatSlopeLayerItem(CopycatSlopeLayerBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(@NotNull BlockPlaceContext context, @NotNull BlockState state) {
        boolean stacking = context.getLevel().getBlockState(context.getClickedPos()).is(getBlock());
        if (!super.placeBlock(context, state)) return false;
        // BlockItem takes the last item, a run piece can be worth more
        Player player = context.getPlayer();
        if (!stacking && player != null && !player.isCreative())
            context.getItemInHand().shrink(CopycatSlopeLayerBlock.getItemCost(state.getValue(CopycatSlopeLayerBlock.LAYERS)) - 1);
        return true;
    }
}
