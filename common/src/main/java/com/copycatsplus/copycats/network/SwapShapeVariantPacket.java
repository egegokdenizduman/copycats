package com.copycatsplus.copycats.network;

import com.copycatsplus.copycats.foundation.shape_variant.ShapeVariants;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Swap the main hand copycat for another variant in its group, keeping the stack size.
 */
public record SwapShapeVariantPacket(Item target, boolean vertical) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, SwapShapeVariantPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), SwapShapeVariantPacket::target,
            ByteBufCodecs.BOOL, SwapShapeVariantPacket::vertical,
            SwapShapeVariantPacket::new
    );

    @Override
    public void handle(ServerPlayer sender) {
        ItemStack held = sender.getMainHandItem();
        ShapeVariants.groupOf(held.getItem()).stream()
                .filter(variant -> variant.matches(target, vertical))
                .findFirst()
                .ifPresent(variant -> sender.setItemInHand(InteractionHand.MAIN_HAND, variant.swap(held)));
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CCPackets.SWAP_SHAPE_VARIANT;
    }
}
