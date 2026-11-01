package com.copycatsplus.copycats.mixin.copycat.slope;

import com.copycatsplus.copycats.content.copycat.slope.SlopeWalking;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @WrapMethod(method = "collide")
    private Vec3 walkOnSlope(Vec3 movement, Operation<Vec3> original) {
        Entity entity = (Entity) (Object) this;
        if (!SlopeWalking.applies(entity, movement))
            return original.call(movement);
        return SlopeWalking.move(entity, movement, original::call);
    }
}
