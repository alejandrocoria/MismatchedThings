package games.alejandrocoria.mismatchedleggings.spike.aether;

import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import com.aetherteam.aetherii.item.components.Charms;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Remaining presentation feasibility only; all gameplay comes from packaged production. */
public final class AetherSpike {
    public static final List<String> FAMILIES = List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune");

    private AetherSpike() {}

    public static void register(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> AetherGameplayChecks.tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(AetherSpikeClient::setup);
        }
    }

    public static ItemStack charmed() {
        var source = item("neptune_leggings");
        source.set(AetherIIDataComponents.CHARMS, new Charms(new Charms.CharmHolder(Charms.Type.ARMOR, Charms.Tier.TWO,
                item("charm_of_health_1")), new Charms.CharmHolder(Charms.Type.ARMOR, Charms.Tier.TWO)));
        return source;
    }

    public static List<ItemStack> pair(ItemStack left, ItemStack right) {
        var pair = MixedLeggingsFactory.createPair(left, right);
        return List.of(pair.primary(), pair.inverse());
    }

    public static ItemStack item(String path) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("aether_ii", path)));
        require(!stack.isEmpty(), "Missing native item: " + path);
        return stack;
    }

    public static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
