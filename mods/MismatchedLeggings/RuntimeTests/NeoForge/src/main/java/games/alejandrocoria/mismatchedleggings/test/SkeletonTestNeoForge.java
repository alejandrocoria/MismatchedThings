package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.test.rootsclassic.RootsTickChecks;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("leggings_skeleton_test")
public final class SkeletonTestNeoForge {
    public SkeletonTestNeoForge(IEventBus bus) {
        if (!games.alejandrocoria.mismatchedleggings.test.silentgear.StateChecks.stage().isEmpty()) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                    games.alejandrocoria.mismatchedleggings.test.silentgear.StateChecks.tick(event.getServer()));
            if (FMLEnvironment.getDist().isClient()) { bus.addListener(SkeletonClientNeoForge::setup); }
            return;
        }
        if (Boolean.getBoolean("leggings.test.sgFoundation")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                    games.alejandrocoria.mismatchedleggings.test.silentgear.FoundationChecks.tick(event.getServer()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.sgSetProof")) {
            games.alejandrocoria.mismatchedleggings.spike.silentgear.SpikeChecks.register();
            return;
        }
        if (Boolean.getBoolean("leggings.test.iafTide")) {
            games.alejandrocoria.mismatchedleggings.test.iceandfire.TideChecks.register();
            return;
        }
        if (Boolean.getBoolean("leggings.test.iafDamage")) {
            games.alejandrocoria.mismatchedleggings.test.iceandfire.DamageChecks.register();
            return;
        }
        if (games.alejandrocoria.mismatchedleggings.test.awakenedset.SetChecks.enabled()) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> games.alejandrocoria.mismatchedleggings.test.awakenedset.SetChecks.tick(event.getServer()));
            return;
        }
        if (games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundChecks.enabled()) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundChecks.tick(event.getServer()));
            return;
        }
        if (games.alejandrocoria.mismatchedleggings.test.mysticaleffects.EffectsChecks.enabled()) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> games.alejandrocoria.mismatchedleggings.test.mysticaleffects.EffectsChecks.seed(event.getServer()));
            if (FMLEnvironment.getDist().isClient()) { bus.addListener(SkeletonClientNeoForge::setup); }
            return;
        }
        if (Boolean.getBoolean("leggings.test.rogdoriumPresentation")) {
            if (FMLEnvironment.getDist().isClient()) {
                bus.addListener(SkeletonClientNeoForge::setup);
            }
            return; // This profile seeds via its dedicated server and checks actual tooltip producers on the client.
        }
        if (Boolean.getBoolean("leggings.test.rogdoriumState")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> RogdoriumStateChecks.tick(event.getServer()));
            return;
        }
        if (Boolean.getBoolean("leggings.test.viewerBootsOnly")) {
            var mods = ModList.get();
            BootsOnlyViewerChecks.environment(mods.isLoaded("mismatchedboots"), mods.isLoaded("mismatchedleggings"),
                    mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count());
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> BootsOnlyViewerChecks.serverTick(event.getServer()));
            if (FMLEnvironment.getDist().isClient()) {
                bus.addListener(SkeletonClientNeoForge::setup);
            }
            return;
        }
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "leggings_skeleton_test");
        components.register("charge", () -> CompositionFixture.CHARGE);
        if (ForbiddenBootsChecks.only()) {
            components.register(bus);
            var mods = ModList.get();
            SkeletonChecks.require(mods.isLoaded("mismatchedboots") && !mods.isLoaded("mismatchedleggings")
                            && mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count() == 1,
                    "Wrong standalone Boots modifier graph");
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ForbiddenArcanusChecks.tick(event.getServer()));
            if (FMLEnvironment.getDist().isClient()) {
                bus.addListener(SkeletonClientNeoForge::setup);
            }
            return;
        }
        if (RecoveryFixture.walletPresent()) {
            components.register("wallet", () -> RecoveryFixture.WALLET);
        }
        components.register(bus);
        if (Boolean.getBoolean("leggings.test.viewers")) {
            var recipes = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "leggings_skeleton_test");
            recipes.register("reversed_workbench", () -> ViewerRecipe.SERIALIZER);
            recipes.register(bus);
        }
        if (RecoveryFixture.sourcesPresent()) {
            var items = DeferredRegister.createItems("leggings_skeleton_test");
            items.register("temporary_leggings", RecoveryFixture::source);
            items.register(bus);
        }
        NeoForgeCompositionChecks.register();
        if (Boolean.getBoolean("leggings.test.advancedNetherite")) {
            var items = DeferredRegister.createItems("leggings_skeleton_test");
            items.registerItem("advanced_hook_leggings", AdvancedNetheriteChecks::hookItem);
            items.register(bus);
        }
        if (Boolean.getBoolean("leggings.test.repair") || Boolean.getBoolean("leggings.test.grindstone")) {
            NeoForgeRepairChecks.register(bus);
        }
        var mods = ModList.get();
        SkeletonChecks.environment(mods.isLoaded("mismatchedboots"),
                mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedleggings")).count(),
                mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count());
        var aether = System.getProperty("leggings.test.aetherGameplay", "");
        if (!aether.isEmpty() && !aether.equals("absent")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks.tick(event.getServer()));
        } else if (!System.getProperty("leggings.test.rootsTicks", "").isEmpty()) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> RootsTickChecks.tick(event.getServer()));
        } else if (Boolean.getBoolean("leggings.test.oritechCapture")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> OritechGameplayChecks.tick(event.getServer()));
        } else if (Boolean.getBoolean("leggings.test.neoVitae")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NeoVitaeChecks.tick(event.getServer()));
        } else if (Boolean.getBoolean("leggings.test.forbiddenArcanus")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ForbiddenArcanusChecks.tick(event.getServer()));
        } else if (Boolean.getBoolean("leggings.test.iceAndFire")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> IceAndFireLifecycleChecks.seed(event.getServer()));
        } else if (Boolean.getBoolean("leggings.test.aquaculture")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> AquacultureChecks.tick(event.getServer()));
        } else if (Boolean.getBoolean("leggings.test.allTheModium")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> AllTheModiumChecks.tick(event.getServer()));
        } else {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SkeletonChecks.serverTick(event.getServer()));
        }
        if (FMLEnvironment.getDist().isClient()) {
            if (Boolean.getBoolean("leggings.test.equipped")) {
                games.alejandrocoria.mismatchedleggings.test.render.NativeEquipmentChecks.register(bus);
            }
            bus.addListener(SkeletonClientNeoForge::setup);
        }
    }
}
