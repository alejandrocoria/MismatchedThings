package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceClientTest;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceServerTest;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorClientTest;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorServerProbe;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("mismatchedboots_runtime_test")
public final class NeoForgeRuntimeTest {
    public NeoForgeRuntimeTest(IEventBus eventBus) {
        if (games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods.MissingModsSpike.enabled()) {
            games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods.MissingModsSpike.initialize(eventBus);
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                                                       games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsClient.tick(Minecraft.getInstance()));
            }
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                                                   games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods.MissingModsSpike.tick(event.getServer()));
            return;
        }
        if (games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.enabled()) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.tick(
                    event.getServer()));
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycleClient.tick(
                        Minecraft.getInstance()));
            }
            return;
        }
        if (games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalNativeTestApi.enabled()) {
            MysticalAugmentsServer.register();
            return;
        }
        if (SeparatorObservations.enabled()) {
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SeparatorClientTest.tick(Minecraft.getInstance()));
            } else {
                NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SeparatorServerProbe.run(event.getServer()));
            }
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.allthemodium", "").isEmpty()) {
            AllTheModiumTest.register();
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.undergarden", "").isEmpty()) {
            UndergardenTest.register();
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.aetherAlkahest", "").isEmpty()) {
            AetherAlkahestTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.aetherTooltipSeed")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> event.getServer().halt(false));
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.aetherIITooltip")) {
            if (FMLEnvironment.getDist().isClient()) {
                AetherIITooltipClientTest.register();
            }
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.aetherIILifecycle", "").isEmpty()) {
            AetherIILifecycleTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.aetherIIClient")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> AetherIIClientTest.observeServer());
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> AetherIIClientTest.tick(Minecraft.getInstance()));
            }
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.aetherIIRecognition")
                || Boolean.getBoolean("mismatchedboots.runtimetest.aetherIICharms")) {
            AetherIICompatibilityTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTooltipTest")) {
            if (FMLEnvironment.getDist().isClient()) {
                SilentGearTooltipClientTest.register();
            }
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearRemainingSeed")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
                games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles.write("silent-gear-remaining-seed-success.txt",
                                                                                              "fresh flat world\n");
                event.getServer().halt(false);
            });
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearRemainingTest")) {
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SilentGearRemainingClientTest.tick(Minecraft.getInstance()));
            }
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearClient")) {
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SilentGearClientTest.tick(Minecraft.getInstance()));
            }
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.silentGearLifecycle", "").isEmpty()) {
            SilentGearLifecycleTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.silentGearAbsent")) {
            NeoForgeSilentGearTest.register();
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.rootsTicks", "").isEmpty()) {
            NeoForgeRootsTickTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.oritechFallTest")) {
            NeoForgeOritechFallTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.neoVitaeCompositionTest")) {
            NeoForgeNeoVitaeCompositionTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireItemBarsTest")) {
            if (FMLEnvironment.getDist().isClient()) {
                NeoForgeJustDireItemBarsTest.register();
            } else {
                NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
                    games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles.write(
                            "just-dire-item-bars-server-success.txt", "server\n");
                    event.getServer().halt(false);
                });
            }
            return;
        }
        if (!System.getProperty("mismatchedboots.runtimetest.justDireRefunds", "").isEmpty()) {
            NeoForgeJustDireRefundsTest.register();
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireArmorWearTest")) {
            if (System.getProperty("mismatchedboots.runtimetest.justDireArmorWearVariant", "").equals("abilities")) {
                NeoForgeJustDireArmorWearTest.registerAbilityWitness();
                NeoForgeJustDireEarthquakeServerTest.register();
            } else {
                NeoForgeJustDireArmorWearTest.register();
            }
            return;
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairTest")) {
            NeoForgeJustDireLavaRepairTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairClientTest")) {
            // The integrated server belongs to the visual transport fixture.
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NeoForgeJustDireLavaRepairClientTest.observeServer(event.getServer()));
        } else if (!System.getProperty("mismatchedboots.runtimetest.justDireLavaLifecycle", "").isEmpty()) {
            NeoForgeJustDireLavaLifecycleTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireEarthquakeServerTest")) {
            NeoForgeJustDireEarthquakeServerTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireEarthquakeStateTest")) {
            NeoForgeJustDireEarthquakeStateTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompServerTest")) {
            NeoForgeJustDireGroundStompServerTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompStateTest")) {
            NeoForgeJustDireGroundStompStateTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireRootUpgradeTest")) {
            NeoForgeJustDireRootUpgradeTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireNegateFallTest")) {
            NeoForgeJustDireNegateFallTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireAttributesTest")) {
            NeoForgeJustDireAttributesTest.register();
        } else if (!System.getProperty("mismatchedboots.runtimetest.justDireResourcesGuard", "").isEmpty()) {
            NeoForgeJustDireResourcesGuardTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireResourcesTest")) {
            NeoForgeJustDireResourcesTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireCooldownHudTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompClientTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompTransportTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.justDireResourcesClientTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.justDireJumpClientTest")) {
            // The integrated server belongs to the client gameplay fixture, not network acceptance.
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireJumpTest")) {
            NeoForgeJustDireJumpTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireSmithingTest")) {
            NeoForgeJustDireSmithingTest.register();
        } else if (!System.getProperty("mismatchedboots.runtimetest.tideOwners", "").isEmpty()) {
            NeoForgeTideTest.register();
        } else if (Boolean.getBoolean("mismatchedboots.runtimetest.iceAndFireDamageTest")) {
            NeoForgeIceAndFireDamageTest.register();
        } else if (!Boolean.getBoolean("mismatchedboots.runtimetest.advancedNetheriteTest")
                && !Boolean.getBoolean("mismatchedboots.runtimetest.geometryTest")) {
            NeoForge.EVENT_BUS.addListener(
                    (ServerTickEvent.Post event) -> NetworkAcceptanceServerTest.tick(event.getServer()));
        }
        if (FMLEnvironment.getDist().isClient()) {
            if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireLavaRepairClientTest")) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> NeoForgeJustDireLavaRepairClientTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireCooldownHudTest")) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> NeoForgeJustDireCooldownHudTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompClientTest")) {
                // Inspect the selected native root after the production Post handlers refresh it.
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                                               (ClientTickEvent.Post event) -> NeoForgeJustDireGroundStompClientTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompTransportTest")) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> NeoForgeJustDireGroundStompTransportTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireResourcesClientTest")) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                                                       NeoForgeJustDireResourcesClientTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireJumpClientTest")) {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                                                       NeoForgeJustDireJumpClientTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.geometryTest")) {
                NeoForgeNativeEquipmentClientTest.registerFixtures(eventBus);
                eventBus.addListener(NeoForgeNativeEquipmentClientTest::registerExtensions);
                eventBus.addListener(NeoForgeNativeEquipmentClientTest::captureRenderer);
                NeoForge.EVENT_BUS.addListener((ClientResourceLoadFinishedEvent event) -> {
                    if (event.isInitial()) {
                        NeoForgeGeometryClientTest.resourcesLoaded();
                    }
                });
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                                                       NeoForgeGeometryClientTest.tick(Minecraft.getInstance()));
            } else if (Boolean.getBoolean("mismatchedboots.runtimetest.tooltipTest")) {
                eventBus.addListener(NeoForgeTooltipClientTest::registerAppenders);
                NeoForge.EVENT_BUS.addListener(NeoForgeTooltipClientTest::onTooltip);
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                                                       NeoForgeTooltipClientTest.tick(Minecraft.getInstance()));
            } else {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                                                       NetworkAcceptanceClientTest.tick(Minecraft.getInstance()));
            }
        }
    }
}
