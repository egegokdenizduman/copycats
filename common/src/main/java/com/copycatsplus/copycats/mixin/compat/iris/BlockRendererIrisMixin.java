package com.copycatsplus.copycats.mixin.compat.iris;

import com.copycatsplus.copycats.compat.Mods;
import com.copycatsplus.copycats.foundation.annotation.ModMixin;
import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateTextureAtlasSprite;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.vertices.sodium.terrain.VertexEncoderInterface;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Give each copycat quad the shader block ID of its material instead of the copycat block,
 * so shader packs apply the material's effects (metallic iron, glowing ores, etc.).
 */
@ModMixin(requiredMods = Mods.IRIS)
@Mixin(value = BlockRenderer.class, remap = false)
@Pseudo
public class BlockRendererIrisMixin {

    @Unique
    private boolean copycats$overridden = false;

    @Inject(method = "bufferQuad", at = @At("HEAD"), require = 0)
    private void copycats$useMaterialId(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
        // Runs for every quad in the world, so look at the sprite first
        if (!(quad.cachedSprite() instanceof MultiStateTextureAtlasSprite sprite) || sprite.getMaterial() == null)
            return;
        // Only Iris builds for Sodium 0.8 (1.8.14+) attach the encoder to BlockRenderer
        if (!((Object) this instanceof VertexEncoderInterface encoder))
            return;
        Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids == null || !ids.containsKey(sprite.getMaterial()))
            return;

        encoder.overrideBlock(ids.getInt(sprite.getMaterial()));
        copycats$overridden = true;
    }

    @Inject(method = "bufferQuad", at = @At("RETURN"), require = 0)
    private void copycats$restoreId(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
        if (copycats$overridden && (Object) this instanceof VertexEncoderInterface encoder) {
            encoder.restoreBlock();
            copycats$overridden = false;
        }
    }
}
