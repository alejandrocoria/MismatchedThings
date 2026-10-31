package games.alejandrocoria.mismatchedleggings.test.render;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.List;

public final class RenderFixture {
    public static List<Sample> gallery = List.of();
    private RenderFixture() {}

    public static ItemStack pair(ItemStack first, ItemStack second) {
        return MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults()).primary();
    }

    public static ItemStack pair(int scenario) {
        var sources = sources(scenario);
        var root = pair(sources[0], sources[1]);
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, scenario == 2);
        return root;
    }

    public static ItemStack[] sources(int scenario) {
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        var gold = new ItemStack(Items.GOLDEN_LEGGINGS);
        if (scenario == 1) {
            return new ItemStack[]{gold, iron};
        }
        if (scenario == 2) {
            var leather = new ItemStack(Items.LEATHER_LEGGINGS);
            leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
            var registry = Minecraft.getInstance().level.registryAccess();
            leather.set(DataComponents.TRIM, new ArmorTrim(
                    registry.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.IRON),
                    registry.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
            return new ItemStack[]{leather, gold};
        }
        return new ItemStack[]{iron, gold};
    }

    public record Sample(String label, ItemStack stack, float rotation) {}
}
