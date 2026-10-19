package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Optional;

/** Root-owned clocks and projection provenance, never another native gear stack. */
public record SilentGearState(Optional<Oxidation> left, Optional<Oxidation> right,
                              List<Rule> rules, ItemAttributeModifiers projection, List<Key> overrides) {
    private static final int MAX_RULES = 64, MAX_ENTRIES = 128;
    private static final Codec<AttributePolicy> POLICY = Codec.STRING.comapFlatMap(
            name -> AttributePolicy.fromSerializedName(name).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown SG projection policy")), AttributePolicy::serializedName);
    public static final Codec<SilentGearState> CODEC = Raw.CODEC.comapFlatMap(raw -> {
        try { return DataResult.success(new SilentGearState(raw.left, raw.right, raw.rules, raw.projection, raw.overrides)); }
        catch (IllegalArgumentException failure) { return DataResult.error(failure::getMessage); }
    }, state -> new Raw(state.left, state.right, state.rules, state.projection, state.overrides));

    public SilentGearState {
        rules = List.copyOf(rules);
        overrides = List.copyOf(overrides);
        projection = new ItemAttributeModifiers(List.copyOf(projection.modifiers()));
        var attributes = rules.stream().map(Rule::attribute).toList();
        if (rules.size() > MAX_RULES || rules.stream().map(Rule::attribute).distinct().count() != rules.size()
                || projection.modifiers().size() > MAX_ENTRIES || overrides.size() > MAX_ENTRIES
                || overrides.stream().distinct().count() != overrides.size()
                || projection.modifiers().stream().anyMatch(entry -> !Double.isFinite(entry.modifier().amount())
                || !attributes.contains(Key.of(entry).attribute))
                || overrides.stream().anyMatch(key -> !attributes.contains(key.attribute))) {
            throw new IllegalArgumentException("Invalid bounded SG projection state");
        }
    }

    public record Oxidation(String phase, int stage, int counter) {
        private static final Codec<String> PHASE = Codec.STRING.validate(value ->
                value.equals("polished") || value.equals("oxidized") ? DataResult.success(value)
                        : DataResult.error(() -> "Unknown SG oxidation phase"));
        public static final Codec<Oxidation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PHASE.fieldOf("phase").forGetter(Oxidation::phase),
                Codec.intRange(0, Byte.MAX_VALUE).fieldOf("stage").forGetter(Oxidation::stage),
                Codec.intRange(0, Integer.MAX_VALUE - 1).fieldOf("counter").forGetter(Oxidation::counter)
        ).apply(instance, Oxidation::new));

        public Oxidation {
            if ((!phase.equals("polished") && !phase.equals("oxidized")) || stage < 0 || stage > Byte.MAX_VALUE
                    || counter < 0 || counter == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid SG oxidation lane");
            }
        }
    }

    public record Rule(Identifier attribute, AttributePolicy policy) {
        private static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("attribute").forGetter(Rule::attribute),
                POLICY.fieldOf("policy").forGetter(Rule::policy)).apply(instance, Rule::new));
    }

    public record Key(Identifier attribute, Identifier modifier, EquipmentSlotGroup slot) {
        private static final Codec<Key> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("attribute").forGetter(Key::attribute),
                Identifier.CODEC.fieldOf("modifier").forGetter(Key::modifier),
                EquipmentSlotGroup.CODEC.fieldOf("slot").forGetter(Key::slot)).apply(instance, Key::new));

        public static Key of(ItemAttributeModifiers.Entry entry) {
            return new Key(entry.attribute().unwrapKey().orElseThrow().identifier(), entry.modifier().id(), entry.slot());
        }
    }

    private record Raw(Optional<Oxidation> left, Optional<Oxidation> right,
                       List<Rule> rules, ItemAttributeModifiers projection, List<Key> overrides) {
        private static final Codec<Raw> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Oxidation.CODEC.optionalFieldOf("left").forGetter(Raw::left),
                Oxidation.CODEC.optionalFieldOf("right").forGetter(Raw::right),
                Rule.CODEC.listOf(0, MAX_RULES).fieldOf("rules").forGetter(Raw::rules),
                ItemAttributeModifiers.CODEC.validate(value -> value.modifiers().size() <= MAX_ENTRIES
                        ? DataResult.success(value) : DataResult.error(() -> "Oversized SG projection"))
                        .fieldOf("projection").forGetter(Raw::projection),
                Key.CODEC.listOf(0, MAX_ENTRIES).fieldOf("overrides").forGetter(Raw::overrides)
        ).apply(instance, Raw::new));
    }
}
