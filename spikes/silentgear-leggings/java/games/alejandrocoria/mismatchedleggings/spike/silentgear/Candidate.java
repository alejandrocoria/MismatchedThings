package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.trait.effect.OxidationTraitEffect;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/** Deliberately incomplete prototype. CUSTOM_DATA is evidence storage, not a proposed published schema. */
public final class Candidate {
    private static final Identifier ARMOR = Identifier.withDefaultNamespace("armor");
    private static final Identifier MAGIC = Identifier.parse("silentgear:magic_armor");
    public static int calls;

    private Candidate() {}

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

    static ItemAttributeModifiers attributes(ItemStack source) {
        var effective = source.getAttributeModifiers();
        return source.getItem() instanceof GearItem
                ? new ItemAttributeModifiers(effective.modifiers().stream().distinct().toList()) : effective;
    }

    static AttributeMerger.Results merge(ItemStack first, ItemStack second) {
        return AttributeMerger.merge(attributes(first), attributes(second),
                id -> id.equals(MAGIC) ? AttributePolicy.AVERAGE : AttributeRuleDefaults.ruleSet().policyFor(id));
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

    static void seed(ItemStack root) {
        var data = root.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (int side = 0; side < 2; side++) {
            var source = sources(root).get(side);
            var lane = capture(source);
            if (lane != null) { data.put("sg_spike_" + side, lane); }
        }
        root.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    private static CompoundTag capture(ItemStack source) {
        var phase = TraitHelper.getTraits(source).stream()
                .filter(t -> List.of("polished", "oxidized").contains(t.getTraitId().getPath())).findFirst().orElse(null);
        if (phase == null) { return null; }
        var lane = new CompoundTag();
        lane.putString("phase", phase.getTraitId().toString());
        lane.putInt("level", phase.getLevel());
        lane.putInt("stage", source.getOrDefault(SgDataComponents.OXIDATION_STAGE.get(), (byte) 0));
        lane.putInt("counter", source.getOrDefault(SgDataComponents.OXIDATION_COUNTER.get(), 0));
        return lane;
    }

    static void advance(ItemStack root, ServerPlayer player) {
        var data = root.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (int side = 0; side < 2; side++) {
            var lane = data.getCompound("sg_spike_" + side).orElse(null);
            if (lane == null) { continue; }
            var work = restore(sources(root).get(side), lane);
            var phase = TraitHelper.getTraits(work).stream().filter(t -> t.getTraitId().toString().equals(lane.getStringOr("phase", ""))).findFirst().orElseThrow();
            for (var effect : phase.getTrait().getEffects()) {
                if (effect instanceof OxidationTraitEffect oxidation) {
                    oxidation.inventoryTick(new TraitActionContext(player, phase, work), player.level(), player, false);
                }
            }
            if (work.has(SgDataComponents.RECALCULATE_FLAG.get())) {
                GearData.recalculateGearData(work, player);
                work.remove(SgDataComponents.RECALCULATE_FLAG.get());
            }
            data.put("sg_spike_" + side, capture(work));
        }
        root.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    private static ItemStack restore(ItemStack source, CompoundTag lane) {
        var work = source.copy();
        var traits = new ArrayList<>(TraitHelper.getTraits(work).stream()
                .filter(t -> !List.of("polished", "oxidized").contains(t.getTraitId().getPath())).toList());
        traits.add(TraitInstance.of(DataResource.trait(lane.getStringOr("phase", "")), lane.getIntOr("level", 0)));
        var values = new HashMap<>(GearData.getProperties(work).properties());
        values.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(traits));
        work.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(values));
        work.set(SgDataComponents.OXIDATION_STAGE.get(), (byte) lane.getIntOr("stage", 0));
        work.set(SgDataComponents.OXIDATION_COUNTER.get(), lane.getIntOr("counter", 0));
        work.remove(SgDataComponents.RECALCULATE_FLAG.get());
        return work;
    }

    static void refreshArmor(ItemStack root, ServerPlayer player) {
        var sources = sources(root);
        var working = new ArrayList<ItemStack>();
        for (var source : sources) {
            var work = source.copy();
            if (work.getItem() instanceof GearItem) {
                int damage = (int) ((long) root.getDamageValue() * work.getMaxDamage() / root.getMaxDamage());
                work.set(DataComponents.DAMAGE, damage);
                GearData.recalculateGearData(work, player);
            }
            working.add(work);
        }
        var projection = merge(working.getFirst(), working.getLast()).primary();
        var current = root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var entries = new ArrayList<>(current.modifiers().stream().filter(e -> !baseArmor(e)).toList());
        entries.addAll(projection.modifiers().stream().filter(Candidate::baseArmor).toList());
        root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
    }

    private static boolean baseArmor(ItemAttributeModifiers.Entry entry) {
        return entry.attribute().unwrapKey().orElseThrow().identifier().equals(ARMOR)
                && entry.modifier().id().equals(Identifier.parse("minecraft:armor.leggings"));
    }
}
