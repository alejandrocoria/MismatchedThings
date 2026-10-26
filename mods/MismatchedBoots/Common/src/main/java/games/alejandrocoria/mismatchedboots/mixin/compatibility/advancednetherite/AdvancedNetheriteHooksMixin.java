package games.alejandrocoria.mismatchedboots.mixin.compatibility.advancednetherite;

import com.autovw.advancednetherite.api.impl.IAdvancedHooks;
import com.autovw.advancednetherite.common.item.AdvancedArmorItem;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MixedBootsItem.class)
public abstract class AdvancedNetheriteHooksMixin implements IAdvancedHooks {
    @Override
    public boolean pacifyEndermen(ItemStack stack) {
        return MixedBootsSources.anySource(stack, AdvancedNetheriteHooksMixin::mismatchedboots$pacifiesEndermen);
    }

    @Override
    public boolean pacifyPhantoms(ItemStack stack) {
        return MixedBootsSources.anySource(stack, AdvancedNetheriteHooksMixin::mismatchedboots$pacifiesPhantoms);
    }

    @Unique
    private static boolean mismatchedboots$pacifiesEndermen(ItemStack source) {
        Item item = source.getItem();
        if (item instanceof AdvancedArmorItem armor && armor.pacifiesEndermen(source)) {
            return true;
        }
        return item instanceof IAdvancedHooks hooks && hooks.pacifyEndermen(source);
    }

    @Unique
    private static boolean mismatchedboots$pacifiesPhantoms(ItemStack source) {
        Item item = source.getItem();
        if (item instanceof AdvancedArmorItem armor && armor.pacifiesPhantoms(source)) {
            return true;
        }
        return item instanceof IAdvancedHooks hooks && hooks.pacifyPhantoms(source);
    }
}
