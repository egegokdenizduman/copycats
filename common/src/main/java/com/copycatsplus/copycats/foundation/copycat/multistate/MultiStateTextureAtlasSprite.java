package com.copycatsplus.copycats.foundation.copycat.multistate;

import com.copycatsplus.copycats.foundation.copycat.model.kinetic.RendererReloadCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;

/**
 * A hack to pass the currently rendering property (and its material) to the block color provider and shader compat.
 */
@ApiStatus.Internal
public class MultiStateTextureAtlasSprite extends TextureAtlasSprite {
    // Cleared after a resource reload, since the wrapped sprites are replaced
    private static final RendererReloadCache<Key, MultiStateTextureAtlasSprite> CACHE = new RendererReloadCache<>();

    private final String property;
    @Nullable
    private final BlockState material;

    private MultiStateTextureAtlasSprite(String property, @Nullable BlockState material, TextureAtlasSprite wrapped) {
        super(wrapped.atlasLocation(), wrapped.contents(),
                Math.round(wrapped.contents().width() / (wrapped.getU1() - wrapped.getU0())),
                Math.round(wrapped.contents().height() / (wrapped.getV1() - wrapped.getV0())),
                wrapped.getX(), wrapped.getY());
        this.property = property;
        this.material = material;
    }

    public static MultiStateTextureAtlasSprite of(String property, @Nullable BlockState material, TextureAtlasSprite wrapped) {
        return CACHE.get(new Key(property, material, wrapped), key -> new MultiStateTextureAtlasSprite(key.property(), key.material(), key.wrapped()));
    }

    public String getProperty() {
        return property;
    }

    @Nullable
    public BlockState getMaterial() {
        return material;
    }

    private record Key(String property, @Nullable BlockState material, TextureAtlasSprite wrapped) {
    }
}
