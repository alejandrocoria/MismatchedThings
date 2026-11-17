package games.alejandrocoria.mismatchedleggings.compatibility.neovitae;

import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeHellforgedService;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Source/schema and logical-side rule authority stay in Leggings. */
public final class NeoVitaeHellforgedCompatibility {
    private static final Identifier MARKER = Identifier.parse("neovitae:hellforged");

    private NeoVitaeHellforgedCompatibility() {
    }

    public static void register(Item item) {
        NeoVitaeHellforgedService.INSTANCE.register(item, NeoVitaeHellforgedCompatibility::sources);
    }

    public static NeoVitaeHellforgedService.@Nullable Sources sources(ItemStack root, boolean client) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return null;
        }
        var rules = client ? CompositionRuleState.synchronizedPreview() : CompositionRuleState.server();
        if (rules == null) {
            return null;
        }
        return new NeoVitaeHellforgedService.Sources(data.left().source(), data.right().source(),
                rules.components().components().ruleFor(MARKER).policy() == ComponentPolicy.SHARE);
    }
}
