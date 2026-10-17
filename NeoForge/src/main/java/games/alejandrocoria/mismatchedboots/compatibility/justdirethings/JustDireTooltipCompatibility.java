package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireTooltipClient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Filter historical source state; client-only native presentation reads the live root. */
public final class JustDireTooltipCompatibility {
    private static final Set<String> BOOTS = Set.of(
            "ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots");
    private static final Set<String> SOURCE_STATE = Set.of(
            "justdirethings.festored", "justdirethings.enabled", "justdirethings.disabled");
    private static final List<String> UPGRADES = List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp", "earthquake");

    private JustDireTooltipCompatibility() {}

    public static Consumer<Component> sourceTooltip(ItemStack source, Consumer<Component> tooltip) {
        var id = BuiltInRegistries.ITEM.getKey(source.getItem());
        if (!id.getNamespace().equals("justdirethings") || !BOOTS.contains(id.getPath())) { return tooltip; }
        return line -> {
            if (line.getContents() instanceof TranslatableContents text) {
                if (SOURCE_STATE.contains(text.getKey())) { return; }
                if (UPGRADES.stream().anyMatch(name -> text.getKey().equals("justdirethings.ability." + name))) { return; }
                // No audited boot has remaining Shift-only lore once Lava moves to root.
                if (text.getKey().equals("justdirethings.shiftmoreinfo")
                        || text.getKey().equals("justdirethings.ability.lavarepair")) { return; }
            }
            tooltip.accept(line);
        };
    }

    public static void appendRootState(ItemStack root, TooltipDisplay display, Consumer<Component> tooltip) {
        // The registered item also exists on dedicated servers: never resolve Shift/key classes there.
        if (FMLEnvironment.getDist() == Dist.CLIENT) { JustDireTooltipClient.appendRootState(root, display, tooltip); }
    }
}
