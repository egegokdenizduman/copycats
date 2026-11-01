package com.copycatsplus.copycats.network;

import com.copycatsplus.copycats.content.copycat.redstone_link.CopycatEncasedRedstoneLinkBlockEntity;
import com.simibubi.create.AllItems;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Show or hide the frequency slots of an encased redstone link.
 */
public record ToggleLinkFrequencySlotsPacket(BlockPos pos) implements ServerboundPacketPayload {

    public static final StreamCodec<ByteBuf, ToggleLinkFrequencySlotsPacket> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(ToggleLinkFrequencySlotsPacket::new, ToggleLinkFrequencySlotsPacket::pos);

    @Override
    public void handle(ServerPlayer sender) {
        Level level = sender.level();
        if (!sender.mayBuild() || !AllItems.WRENCH.isIn(sender.getMainHandItem()))
            return;
        if (!sender.canInteractWithBlock(pos, 1) || !level.mayInteract(sender, pos))
            return;
        if (level.getBlockEntity(pos) instanceof CopycatEncasedRedstoneLinkBlockEntity link)
            link.toggleFrequencySlots();
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CCPackets.TOGGLE_LINK_FREQUENCY_SLOTS;
    }
}
