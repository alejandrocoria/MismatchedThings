package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SkeletonClientNeoForge {
    private SkeletonClientNeoForge() {
    }

    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("leggings.test.allTheModium")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> AllTheModiumClientChecks.tick(Minecraft.getInstance()));
        } else {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> SkeletonClientChecks.tick(Minecraft.getInstance()));
        }
    }
}
