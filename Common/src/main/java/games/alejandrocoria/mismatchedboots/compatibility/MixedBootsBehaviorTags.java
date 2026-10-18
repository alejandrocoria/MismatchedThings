package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class MixedBootsBehaviorTags {
    private static final TagKey<Item> CRYSTALITE_SET = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("betterend", "armor_set/crystalite"));
    private static final TagKey<Item> SENTIENT_SET = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("neovitae", "sentient_upgrade_set/sentient"));
    private static final TagKey<Item> SENTIENT_UPGRADE_SET = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("neovitae", "sentient_upgrade_set"));
    private static final TagKey<Item> SPIRITUS_CAPABLE = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("neovitae", "spiritus_capable"));
    private static final Set<TagKey<Item>> ANY_SOURCE = Set.of(
            ItemTags.PIGLIN_SAFE_ARMOR,
            CRYSTALITE_SET,
            SENTIENT_SET,
            SENTIENT_UPGRADE_SET,
            SPIRITUS_CAPABLE);

    private MixedBootsBehaviorTags() {
    }

    public static boolean projects(ItemStack stack, TagKey<?> tag) {
        TagKey<Item> sourceTag = projectedTag(tag);
        if (sourceTag == null
                || !stack.is(ModContent.MIXED_BOOTS_KEY)
                || !(stack.getItem() instanceof MixedBootsItem)) {
            return false;
        }
        return MixedBootsSources.anySource(stack, source -> source.is(sourceTag));
    }

    static boolean isProjected(TagKey<?> tag) {
        return projectedTag(tag) != null;
    }

    private static TagKey<Item> projectedTag(TagKey<?> tag) {
        for (TagKey<Item> projected : ANY_SOURCE) {
            if (projected.equals(tag)) {
                return projected;
            }
        }
        return null;
    }
}
