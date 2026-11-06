package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SkeletonClientNeoForge {
    private SkeletonClientNeoForge() {
    }

    public static void setup(FMLClientSetupEvent event) {
        if (ForbiddenBootsChecks.enabled()) {
            SkeletonChecks.require(ForbiddenBootsStorage.adoptions.get() == 0, "Native modifier adoption ran before a client world existed");
        }
        if (Boolean.getBoolean("leggings.test.forbiddenArcanus")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> ForbiddenArcanusClientChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.aquaculture")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> AquacultureClientChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.allTheModium")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> AllTheModiumClientChecks.tick(Minecraft.getInstance()));
        } else {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> SkeletonClientChecks.tick(Minecraft.getInstance()));
        }
    }
}
