package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public final class MixedBootsBehaviorTags {
    private MixedBootsBehaviorTags() {
    }

    public static boolean projects(ItemStack stack, TagKey<?> tag) {
        if (!ItemTags.PIGLIN_SAFE_ARMOR.equals(tag)
                || !stack.is(ModContent.MIXED_BOOTS_KEY)
                || !(stack.getItem() instanceof MixedBootsItem)) {
            return false;
        }
        return MixedBootsSources.anySource(stack, source -> source.is(ItemTags.PIGLIN_SAFE_ARMOR));
    }
}
