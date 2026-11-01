package com.copycatsplus.copycats.foundation.copycat.multistate;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;

/**
 * A hack to pass the currently rendering property (and its material) to the block color provider and shader compat.
 */
@ApiStatus.Internal
public class MultiStateTextureAtlasSprite extends TextureAtlasSprite {
    private final String property;
    @Nullable
    private final BlockState material;

    public MultiStateTextureAtlasSprite(String property, @Nullable BlockState material, TextureAtlasSprite wrapped) {
        super(wrapped.atlasLocation(), wrapped.contents(), (int) (wrapped.getX() / wrapped.getU0()), (int) (wrapped.getY() / wrapped.getV0()), wrapped.getX(), wrapped.getY());
        this.property = property;
        this.material = material;
    }

    public String getProperty() {
        return property;
    }

    @Nullable
    public BlockState getMaterial() {
        return material;
    }
}
