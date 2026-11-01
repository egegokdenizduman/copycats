package com.copycatsplus.copycats.foundation.shape_variant;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.config.FeatureToggle;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Arrays;
import java.util.List;

/**
 * Groups of copycats that the held item can be swapped between.
 */
public class ShapeVariants {

    private static final List<List<Variant>> GROUPS = List.of(
            List.of(
                    new Variant(CCBlocks.COPYCAT_SLOPE, false),
                    new Variant(CCBlocks.COPYCAT_SLOPE_LAYER, false),
                    new Variant(CCBlocks.COPYCAT_VERTICAL_SLOPE, false),
                    new Variant(CCBlocks.COPYCAT_SLOPE_LAYER, true)
            ),
            group(CCBlocks.COPYCAT_SLAB, CCBlocks.COPYCAT_LAYER),
            group(CCBlocks.COPYCAT_HALF_PANEL, CCBlocks.COPYCAT_HALF_LAYER, CCBlocks.COPYCAT_VERTICAL_HALF_LAYER, CCBlocks.COPYCAT_STACKED_HALF_LAYER),
            group(CCBlocks.COPYCAT_STAIRS, CCBlocks.COPYCAT_VERTICAL_STAIRS),
            group(CCBlocks.COPYCAT_SLICE, CCBlocks.COPYCAT_VERTICAL_SLICE),
            group(CCBlocks.COPYCAT_BEAM, CCBlocks.COPYCAT_VERTICAL_STEP)
    );

    private static List<Variant> group(ItemLike... items) {
        return Arrays.stream(items).map(item -> new Variant(item, false)).toList();
    }

    /**
     * @return The enabled variants in the group of this item, or an empty list if it has none.
     */
    public static List<Variant> groupOf(Item item) {
        return GROUPS.stream()
                .filter(group -> group.stream().anyMatch(variant -> variant.item().asItem() == item))
                .findFirst()
                .map(group -> group.stream().filter(Variant::isEnabled).toList())
                .orElse(List.of());
    }

    /**
     * @param vertical Whether this is the vertical form of a slope layer, which is a state and not an item.
     */
    public record Variant(ItemLike item, boolean vertical) {

        public boolean matches(Item item, boolean vertical) {
            return this.item.asItem() == item && this.vertical == vertical;
        }

        public boolean matches(ItemStack stack) {
            return matches(stack.getItem(), CopycatSlopeLayerBlock.isVerticalItem(stack));
        }

        public ItemStack swap(ItemStack stack) {
            ItemStack swapped = stack.transmuteCopy(item);
            swapped.remove(DataComponents.CUSTOM_DATA);
            if (vertical)
                CopycatSlopeLayerBlock.markVerticalItem(swapped);
            return swapped;
        }

        private boolean isEnabled() {
            return FeatureToggle.isEnabled(BuiltInRegistries.ITEM.getKey(item.asItem()));
        }
    }
}
