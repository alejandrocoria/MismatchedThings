package games.alejandrocoria.mismatchedleggings.mixin.compatibility.advancednetherite;

import com.autovw.advancednetherite.api.impl.IAdvancedHooks;
import com.autovw.advancednetherite.common.item.AdvancedArmorItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

@Mixin(MixedLeggingsItem.class)
public abstract class AdvancedNetheriteHooksMixin implements IAdvancedHooks {
    @Override
    public boolean pacifyEndermen(ItemStack stack) {
        return mismatchedleggings$anySource(stack, AdvancedNetheriteHooksMixin::mismatchedleggings$pacifiesEndermen);
    }

    @Override
    public boolean pacifyPhantoms(ItemStack stack) {
        return mismatchedleggings$anySource(stack, AdvancedNetheriteHooksMixin::mismatchedleggings$pacifiesPhantoms);
    }

    @Unique
    private static boolean mismatchedleggings$anySource(ItemStack stack, Predicate<ItemStack> predicate) {
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        return data != null && (predicate.test(data.left().source()) || predicate.test(data.right().source()));
    }

    @Unique
    private static boolean mismatchedleggings$pacifiesEndermen(ItemStack source) {
        var item = source.getItem();
        return item instanceof AdvancedArmorItem armor && armor.pacifiesEndermen(source)
                || item instanceof IAdvancedHooks hooks && hooks.pacifyEndermen(source);
    }

    @Unique
    private static boolean mismatchedleggings$pacifiesPhantoms(ItemStack source) {
        var item = source.getItem();
        return item instanceof AdvancedArmorItem armor && armor.pacifiesPhantoms(source)
                || item instanceof IAdvancedHooks hooks && hooks.pacifyPhantoms(source);
    }
}
