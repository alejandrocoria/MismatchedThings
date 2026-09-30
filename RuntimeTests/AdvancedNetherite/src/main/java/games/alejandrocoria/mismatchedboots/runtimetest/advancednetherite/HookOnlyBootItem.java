package games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite;

import com.autovw.advancednetherite.api.impl.IAdvancedHooks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class HookOnlyBootItem extends Item implements IAdvancedHooks {
    public HookOnlyBootItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean pacifyEndermen(ItemStack stack) {
        return (stack.getDamageValue() & 1) != 0;
    }

    @Override
    public boolean pacifyPhantoms(ItemStack stack) {
        return (stack.getDamageValue() & 2) != 0;
    }
}
