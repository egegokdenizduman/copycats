package com.copycatsplus.copycats.foundation.shape_variant;

import com.copycatsplus.copycats.CCKeys;
import com.copycatsplus.copycats.CCLang;
import com.copycatsplus.copycats.foundation.shape_variant.ShapeVariants.Variant;
import com.copycatsplus.copycats.network.CCPackets;
import com.copycatsplus.copycats.network.SwapShapeVariantPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Radial menu for swapping the held copycat for another shape, laid out like Create's toolbox menu.
 */
public class ShapeVariantMenu extends AbstractSimiScreen {

    private static final int SLOTS = 8;

    private final List<Variant> variants;
    private int ticksOpen;
    private int hoveredSlot = -1;

    private ShapeVariantMenu(List<Variant> variants) {
        this.variants = variants;
    }

    public static void onKeyInput(int key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (action != GLFW.GLFW_PRESS || mc.screen != null || mc.player == null)
            return;
        if (!CCKeys.SHAPE_VARIANT_MENU.getKeybind().matches(key, scanCode))
            return;
        List<Variant> variants = ShapeVariants.groupOf(mc.player.getMainHandItem().getItem());
        if (variants.size() > 1)
            ScreenOpener.open(new ShapeVariantMenu(variants));
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        float fade = Mth.clamp((ticksOpen + AnimationTickHolder.getPartialTicks()) / 10f, 1 / 512f, 1);
        ItemStack held = minecraft.player.getMainHandItem();

        hoveredSlot = -1;
        float hoveredX = mouseX - width / 2f;
        float hoveredY = mouseY - height / 2f;
        float distance = hoveredX * hoveredX + hoveredY * hoveredY;
        if (distance > 25 && distance < 10000)
            hoveredSlot = (Mth.floor(AngleHelper.deg(Mth.atan2(hoveredY, hoveredX)) + 360 + 180 - 22.5f) % 360) / 45;

        Component tip = null;
        PoseStack ms = graphics.pose();
        for (int slot = 0; slot < SLOTS; slot++) {
            double angle = Math.toRadians(slot * 45 - 45);
            double radius = 40 - 10 * (1 - fade) * (1 - fade);
            ms.pushPose();
            ms.translate(width / 2f + Math.sin(angle) * radius - 12, height / 2f - Math.cos(angle) * radius - 12, 0);

            if (slot < variants.size()) {
                Variant variant = variants.get(slot);
                ItemStack stack = variant.swap(held);
                boolean current = variant.matches(held);

                (current ? AllGuiTextures.TOOLBELT_INACTIVE_SLOT : AllGuiTextures.TOOLBELT_SLOT).render(graphics, 0, 0);
                GuiGameElement.of(stack).at(3, 3).render(graphics);

                if (slot == hoveredSlot) {
                    if (!current)
                        AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, -1, -1);
                    tip = variant.vertical() ? CCLang.translateDirect("gui.vertical_slope_layer") : stack.getHoverName();
                }
            } else {
                AllGuiTextures.TOOLBELT_EMPTY_SLOT.render(graphics, 0, 0);
            }
            ms.popPose();
        }

        int alpha = (int) (fade * 255);
        if (tip != null && alpha > 8)
            graphics.drawCenteredString(font, tip, width / 2, height - 72, 0xFFFFFF | alpha << 24);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Color color = BACKGROUND_COLOR
                .scaleAlpha(Math.min(1, (ticksOpen + AnimationTickHolder.getPartialTicks()) / 20f));
        graphics.fillGradient(0, 0, width, height, color.getRGB(), color.getRGB());
    }

    @Override
    public void tick() {
        ticksOpen++;
        super.tick();
    }

    @Override
    public void removed() {
        super.removed();
        if (hoveredSlot < 0 || hoveredSlot >= variants.size())
            return;
        Variant variant = variants.get(hoveredSlot);
        if (!variant.matches(minecraft.player.getMainHandItem()))
            CCPackets.network().sendToServer(new SwapShapeVariantPacket(variant.item().asItem(), variant.vertical()));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && hoveredSlot >= 0 && hoveredSlot < variants.size()) {
            onClose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyReleased(int code, int scanCode, int modifiers) {
        if (CCKeys.SHAPE_VARIANT_MENU.getKeybind().matches(code, scanCode)) {
            onClose();
            return true;
        }
        return super.keyReleased(code, scanCode, modifiers);
    }
}
