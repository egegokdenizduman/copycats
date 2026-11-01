package com.copycatsplus.copycats.content.copycat.redstone_link;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CopycatEncasedRedstoneLinkBlockEntity extends RedstoneLinkBlockEntity implements ICopycatBlockEntity {

    protected BlockState material;
    protected ItemStack consumedItem;
    protected boolean enableCT;
    private boolean signalChanged;
    private boolean frequencySlotsVisible;

    public CopycatEncasedRedstoneLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        ICopycatBlockEntity.super.init();
    }

    public void toggleFrequencySlots() {
        frequencySlotsVisible = !frequencySlotsVisible;
        notifyUpdate();
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, worldPosition, 1, frequencySlotsVisible ? 1.25f : .75f);
    }

    // Create draws and clicks the slots through this lookup, while the link network keeps its own reference
    @Override
    public <T extends BlockEntityBehaviour> T getBehaviour(BehaviourType<T> type) {
        return type == LinkBehaviour.TYPE && !frequencySlotsVisible ? null : super.getBehaviour(type);
    }

    @Override
    public void setSignal(int power) {
        if (getReceivedSignal() != power)
            signalChanged = true;
        super.setSignal(power);
    }

    // The receiver half of the base tick only runs for Create's own link block
    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide || isTransmitterBlock())
            return;

        BlockState state = getBlockState();
        if ((getReceivedSignal() > 0) != state.getValue(RedstoneLinkBlock.POWERED)) {
            signalChanged = true;
            level.setBlockAndUpdate(worldPosition, state.cycle(RedstoneLinkBlock.POWERED));
        }
        if (signalChanged) {
            signalChanged = false;
            updateSelfAndAttached(state);
        }
    }

    @Override
    public BlockState getMaterial() {
        return material;
    }

    @Override
    public ItemStack getConsumedItem() {
        return consumedItem;
    }

    @Override
    public boolean isCTEnabled() {
        return enableCT;
    }

    @Override
    public void setMaterialInternal(BlockState material) {
        this.material = material;
    }

    @Override
    public void setConsumedItemInternal(ItemStack consumedItem) {
        this.consumedItem = consumedItem;
    }

    @Override
    public void setCTEnabledInternal(boolean value) {
        enableCT = value;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ICopycatBlockEntity.super.onLoad();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        return ICopycatBlockEntity.super.getRequiredItems(state).union(super.getRequiredItems(state));
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ICopycatBlockEntity.read(this, tag, registries, clientPacket);
        frequencySlotsVisible = tag.getBoolean("FrequencySlotsVisible");
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        ICopycatBlockEntity.writeSafe(this, tag, registries);
        tag.putBoolean("FrequencySlotsVisible", frequencySlotsVisible);
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ICopycatBlockEntity.write(this, tag, registries, clientPacket);
        tag.putBoolean("FrequencySlotsVisible", frequencySlotsVisible);
    }
}
