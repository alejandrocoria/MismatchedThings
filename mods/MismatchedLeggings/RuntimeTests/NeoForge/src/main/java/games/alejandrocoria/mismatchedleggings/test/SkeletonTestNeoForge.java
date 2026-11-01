package games.alejandrocoria.mismatchedleggings.test;

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
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "leggings_skeleton_test");
        components.register("charge", () -> CompositionFixture.CHARGE);
        if (RecoveryFixture.walletPresent()) {
            components.register("wallet", () -> RecoveryFixture.WALLET);
        }
        components.register(bus);
        if (RecoveryFixture.sourcesPresent()) {
            var items = DeferredRegister.createItems("leggings_skeleton_test");
            items.register("temporary_leggings", RecoveryFixture::source);
            items.register(bus);
        }
        NeoForgeCompositionChecks.register();
        if (Boolean.getBoolean("leggings.test.repair") || Boolean.getBoolean("leggings.test.grindstone")) {
            NeoForgeRepairChecks.register(bus);
        }
        var mods = ModList.get();
        SkeletonChecks.environment(mods.isLoaded("mismatchedboots"),
                mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedleggings")).count(),
                mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SkeletonChecks.serverTick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            if (Boolean.getBoolean("leggings.test.equipped")) {
                games.alejandrocoria.mismatchedleggings.test.render.NativeEquipmentChecks.register(bus);
            }
            bus.addListener(SkeletonClientNeoForge::setup);
        }
    }
}
