package games.alejandrocoria.mismatchedboots.compatibility.neovitae;

import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeHellforgedService;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Source/schema and logical-side rule authority stay in Boots. */
public final class NeoVitaeHellforgedCompatibility {
    private static final Identifier MARKER = Identifier.parse("neovitae:hellforged");

    private NeoVitaeHellforgedCompatibility() {
    }

    public static void register(Item item) {
        NeoVitaeHellforgedService.INSTANCE.register(item, NeoVitaeHellforgedCompatibility::sources);
    }

    public static NeoVitaeHellforgedService.@Nullable Sources sources(ItemStack root, boolean client) {
        if (root.isEmpty() || root.getCount() != 1 || ModContent.suspendedBootsData(root).isPresent()) {
            return null;
        }
        var data = ModContent.mixedBootsDataType().map(root::get).orElse(null);
        if (data == null) {
            return null;
        }
        var left = data.left().source();
        var right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) {
            return null;
        }
        var preview = client ? CompositionRulesClientLifecycle.synchronizedRules() : null;
        if (client && preview == null) {
            return null;
        }
        var rules = client ? preview.components() : ComponentRulesLifecycle.serverRules();
        return new NeoVitaeHellforgedService.Sources(left, right, rules.components().ruleFor(MARKER).policy() == ComponentPolicy.SHARE);
    }
}
