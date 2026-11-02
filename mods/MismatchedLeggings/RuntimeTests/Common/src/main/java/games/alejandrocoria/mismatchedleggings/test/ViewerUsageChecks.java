package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Native usage indexing must not advertise bare mixed roots as transformed examples. */
public final class ViewerUsageChecks {
    public record Sample(String label, ItemStack root) {
    }

    private ViewerUsageChecks() {
    }

    public static List<Sample> samples(boolean boots) {
        var result = new ArrayList<Sample>();
        var pair = MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS));
        result.add(new Sample("leggings/bare", new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID))));
        result.add(new Sample("leggings/primary", pair.primary()));
        result.add(new Sample("leggings/inverse", pair.inverse()));
        result.add(new Sample("leggings/ordinary", new ItemStack(Items.IRON_LEGGINGS)));
        if (boots) {
            try {
                var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                var bootsPair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                        .invoke(null, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
                result.add(new Sample("boots/bare", new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("mismatchedboots:mixed_boots")))));
                result.add(new Sample("boots/primary", (ItemStack) bootsPair.getClass().getMethod("primary").invoke(bootsPair)));
                result.add(new Sample("boots/inverse", (ItemStack) bootsPair.getClass().getMethod("inverse").invoke(bootsPair)));
                result.add(new Sample("boots/ordinary", new ItemStack(Items.IRON_BOOTS)));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot create optional current Boots usage samples", failure);
            }
        }
        return result;
    }

    public static boolean isMixed(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("mismatchedboots:mixed_boots") || id.equals("mismatchedleggings:mixed_leggings");
    }
}
