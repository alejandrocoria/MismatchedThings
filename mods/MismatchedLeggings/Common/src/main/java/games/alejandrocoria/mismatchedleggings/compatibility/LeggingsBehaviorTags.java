package games.alejandrocoria.mismatchedleggings.compatibility;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Only audited behavior queries; classification, recipes and arbitrary tags are not source unions. */
public final class LeggingsBehaviorTags {
    private static final List<TagKey<Item>> ANY_SOURCE = List.of(
            ItemTags.PIGLIN_SAFE_ARMOR,
            ItemTags.FREEZE_IMMUNE_WEARABLES,
            TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:alkahest_resistant_item")),
            TagKey.create(Registries.ITEM, Identifier.parse("betterend:armor_set/crystalite")),
            TagKey.create(Registries.ITEM, Identifier.parse("neovitae:sentient_upgrade_set/sentient")),
            TagKey.create(Registries.ITEM, Identifier.parse("neovitae:sentient_upgrade_set")),
            TagKey.create(Registries.ITEM, Identifier.parse("neovitae:spiritus_capable")));

    private LeggingsBehaviorTags() {
    }

    public static boolean projects(ItemStack stack, TagKey<?> tag) {
        for (var sourceTag : ANY_SOURCE) {
            if (!sourceTag.equals(tag)) {
                continue;
            }
            var data = MixedLeggingsStructure.validData(stack).orElse(null);
            return data != null && (data.left().source().is(sourceTag) || data.right().source().is(sourceTag));
        }
        return false;
    }
}
