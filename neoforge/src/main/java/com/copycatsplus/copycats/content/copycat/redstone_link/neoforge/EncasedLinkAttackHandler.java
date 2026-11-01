package com.copycatsplus.copycats.content.copycat.redstone_link.neoforge;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.network.CCPackets;
import com.copycatsplus.copycats.network.ToggleLinkFrequencySlotsPacket;
import com.simibubi.create.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Shift + left click with a wrench toggles the frequency slots of an encased link. Cancelling here means mining never
 * starts, in survival or creative, so the server sees no break attempt.
 */
public class EncasedLinkAttackHandler {

    private static int lastAttackTick = -2;

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!event.isAttack() || player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit))
            return;
        BlockPos pos = hit.getBlockPos();
        if (!CCBlocks.COPYCAT_ENCASED_REDSTONE_LINK.has(mc.level.getBlockState(pos)))
            return;
        if (!player.isShiftKeyDown() || !player.mayBuild() || !AllItems.WRENCH.isIn(player.getMainHandItem()))
            return;
        if (mc.gameMode == null || mc.gameMode.isDestroying())
            return;

        // This event repeats every tick while the button is held
        boolean held = player.tickCount - lastAttackTick <= 1;
        lastAttackTick = player.tickCount;
        event.setCanceled(true);
        event.setSwingHand(!held);
        if (!held)
            CCPackets.network().sendToServer(new ToggleLinkFrequencySlotsPacket(pos));
    }
}
