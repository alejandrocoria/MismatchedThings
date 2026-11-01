package games.alejandrocoria.mismatchedleggings.compatibility;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/** Only audited behavior queries; classification, recipes and arbitrary tags are not source unions. */
public final class LeggingsBehaviorTags {
    private LeggingsBehaviorTags() {
    }

    public static boolean projects(ItemStack stack, TagKey<?> tag) {
        if (!ItemTags.PIGLIN_SAFE_ARMOR.equals(tag) && !ItemTags.FREEZE_IMMUNE_WEARABLES.equals(tag)) {
            return false;
        }
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        if (data == null) {
            return false;
        }
        var sourceTag = ItemTags.PIGLIN_SAFE_ARMOR.equals(tag)
                ? ItemTags.PIGLIN_SAFE_ARMOR : ItemTags.FREEZE_IMMUNE_WEARABLES;
        return data.left().source().is(sourceTag) || data.right().source().is(sourceTag);
    }
}
