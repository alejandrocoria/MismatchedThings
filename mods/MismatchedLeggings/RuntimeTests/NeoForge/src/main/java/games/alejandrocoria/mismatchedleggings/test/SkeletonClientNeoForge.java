package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.test.rootsclassic.RootsRenderChecks;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SkeletonClientNeoForge {
    private SkeletonClientNeoForge() {
    }

    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("leggings.test.jdtSettings")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> JustDireSettingsClientChecks.tick(Minecraft.getInstance()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.jdtConnected")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> JustDireConnectedClient.tick(Minecraft.getInstance()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.sgTooltipClient")) {
            games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipClient.register();
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipClient.tick(Minecraft.getInstance()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.sgRemainingClient")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> games.alejandrocoria.mismatchedleggings.test.silentgear.RemainingLifecycleClient.tick(Minecraft.getInstance()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.sgDefenseClient")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseLifecycleClient.tick(Minecraft.getInstance()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.sgMagnetClient")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> games.alejandrocoria.mismatchedleggings.test.silentgear.MagnetLifecycleClient.tick(Minecraft.getInstance()));
            return;
        }
        if (!games.alejandrocoria.mismatchedleggings.test.silentgear.StateChecks.stage().isEmpty()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> games.alejandrocoria.mismatchedleggings.test.silentgear.StateClient.tick(Minecraft.getInstance()));
            return;
        }
        if (games.alejandrocoria.mismatchedleggings.test.mysticaleffects.EffectsChecks.enabled()) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> games.alejandrocoria.mismatchedleggings.test.mysticaleffects.EffectsClient.tick(Minecraft.getInstance()));
            return;
        }
        if (ForbiddenBootsChecks.enabled()) {
            SkeletonChecks.require(ForbiddenBootsStorage.adoptions.get() == 0, "Native modifier adoption ran before a client world existed");
        }
        if (Boolean.getBoolean("leggings.test.rogdoriumPresentation")) {
            RogdoriumPresentationClient.register();
        } else if (Boolean.getBoolean("leggings.test.aetherTooltips")) {
            games.alejandrocoria.mismatchedleggings.test.aether.AetherTooltipChecks.register();
        } else if (Boolean.getBoolean("leggings.test.rootsRendering")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> RootsRenderChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.oritechCapture")) {
            OritechCaptureChecks.register();
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> OritechCaptureChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.forbiddenArcanus")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> ForbiddenArcanusClientChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.iceAndFire")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> IceAndFireSessionChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.aquaculture")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> AquacultureClientChecks.tick(Minecraft.getInstance()));
        } else if (Boolean.getBoolean("leggings.test.allTheModium")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> AllTheModiumClientChecks.tick(Minecraft.getInstance()));
        } else {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> SkeletonClientChecks.tick(Minecraft.getInstance()));
        }
    }
}
