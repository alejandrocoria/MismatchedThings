package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Disposable candidate: extend only native protection predicates, never root tier metadata or fluid behavior. */
public final class AetherAlkahestSpikeControl {
    static final TagKey<Item> RESISTANCE = TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:alkahest_resistant_item"));
    static int fluidReads, armorReads, fluidExtensions, armorExtensions, tagExtensions;

    private AetherAlkahestSpikeControl() {}

    public static boolean tag(boolean original, ItemStack stack, TagKey<?> tag) {
        if (original || !RESISTANCE.equals(tag)) { return original; }
        boolean extended = MixedBootsSources.anySource(stack, source -> source.is(RESISTANCE));
        if (extended) { tagExtensions++; }
        return extended;
    }

    public static boolean tier(boolean original, ItemStack stack, Supplier<?> supplier, boolean armor) {
        if (armor) { armorReads++; } else { fluidReads++; }
        if (original || supplier.get() != AetherIICompatibilityTest.component("reinforcement_tier")) { return original; }
        boolean extended = MixedBootsSources.anySource(stack, source -> source.has(AetherIICompatibilityTest.component("reinforcement_tier")));
        if (extended) { if (armor) { armorExtensions++; } else { fluidExtensions++; } }
        return extended;
    }
}
