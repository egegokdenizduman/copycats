package com.copycatsplus.copycats.mixin.foundation.copycat;

import com.copycatsplus.copycats.compat.Mods;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public class BlockStateBaseMixin {

    @Unique
    private static final ResourceLocation COPYCAT_BASE = ResourceLocation.fromNamespaceAndPath(Mods.CREATE.id(), "copycat_base");

    // 0 = not computed, 1 = no override, 2 = return false
    @Unique
    private byte copycats$occlusionOverride;

    @Inject(
            method = "canOcclude",
            at = @At("HEAD"),
            cancellable = true
    )
    private void customOcclusion(CallbackInfoReturnable<Boolean> cir) {
        byte override = copycats$occlusionOverride;
        if (override == 0) {
            BlockBehaviour.BlockStateBase instance = (BlockBehaviour.BlockStateBase) (Object) this;
            try {
                override = instance.getBlock() instanceof BracketBlock || instance.getBlockHolder().is(COPYCAT_BASE) ? (byte) 2 : (byte) 1;
                copycats$occlusionOverride = override;
            } catch (IllegalStateException e) {
                // todo: illegal access if resource location is accessed before registry is initialized
            }
        }
        if (override == 2) {
            cir.setReturnValue(false);
        }
    }
}
