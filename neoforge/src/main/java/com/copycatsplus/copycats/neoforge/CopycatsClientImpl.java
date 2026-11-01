package com.copycatsplus.copycats.neoforge;

import com.copycatsplus.copycats.Copycats;
import com.copycatsplus.copycats.CopycatsClient;
import com.copycatsplus.copycats.foundation.copycat.model.kinetic.RendererReloadCache;
import com.copycatsplus.copycats.foundation.shape_variant.ShapeVariantMenu;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLModContainer;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Copycats.MODID, dist = Dist.CLIENT)
public class CopycatsClientImpl {

    public CopycatsClientImpl(FMLModContainer container, IEventBus modBus) {
        CopycatsClient.init();
        NeoForge.EVENT_BUS.<ReloadLevelRendererEvent>addListener(RendererReloadCache::onReloadLevelRenderer);
        NeoForge.EVENT_BUS.<InputEvent.Key>addListener(event -> ShapeVariantMenu.onKeyInput(event.getKey(), event.getScanCode(), event.getAction()));
    }
}
