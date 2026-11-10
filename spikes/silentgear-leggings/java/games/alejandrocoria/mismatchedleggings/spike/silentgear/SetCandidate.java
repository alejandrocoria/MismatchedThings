package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/** Isolated Stellar Wielder feasibility proof; no numeric/state engine or production dispatch. */
public final class SetCandidate {
    public static int calls;

    private SetCandidate() {}

    static List<ItemStack> sources(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        return List.of(data.left().source(), data.right().source());
    }

    static List<TraitInstance> traits(ItemStack root) {
        var traits = new TreeMap<Identifier, TraitInstance>();
        for (var source : sources(root)) {
            for (var trait : TraitHelper.getTraits(source)) {
                traits.merge(trait.getTraitId(), trait, (a, b) -> a.getLevel() >= b.getLevel() ? a : b);
            }
        }
        return List.copyOf(traits.values());
    }

    public static boolean recognizes(Object context, EquipmentSlot slot, ItemStack root) {
        if (slot != EquipmentSlot.LEGS || root.getCount() != 1 || MixedLeggingsStructure.validData(root).isEmpty()
                || root.get(DataComponents.EQUIPPABLE) == null || root.get(DataComponents.EQUIPPABLE).slot() != EquipmentSlot.LEGS) {
            return false;
        }
        var trait = ((TraitActionContext) context).trait();
        return traits(root).stream().anyMatch(t -> t.getTrait() == trait);
    }

    public static boolean unrecognized(Object context, EquipmentSlot slot, ItemStack root) {
        return recognizes(context, slot, root); // Fault profile changes provenance, not runtime semantics.
    }

    static ItemStack carrier(ItemStack root) {
        var carrier = sources(root).stream().filter(s -> s.getItem() instanceof GearItem).findFirst().orElseThrow().copy();
        var map = new HashMap<>(GearData.getProperties(carrier).properties());
        map.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(traits(root)));
        carrier.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(map));
        return carrier;
    }

    static void wearer(ItemStack root, ServerPlayer player) {
        var carrier = carrier(root);
        for (var trait : traits(root)) {
            if (!trait.getTraitId().equals(Identifier.parse("silentgear:stellar"))) { continue; }
            for (var effect : trait.getTrait().getEffects()) {
                if (effect instanceof WielderEffectTraitEffect wielder && ((SetAccess) wielder).spike$ready()) {
                    var before = carrier.copy();
                    calls++;
                    wielder.inventoryTick(new TraitActionContext(player, trait, carrier), player.level(), player, true);
                    SpikeChecks.require(ItemStack.matches(before, carrier), "Wielder mutated its disposable carrier");
                }
            }
        }
    }

}
