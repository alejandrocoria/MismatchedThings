package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Optional;

/** Independent clocks and provenance for the sole managed identity: armor/armor.leggings/LEGS.
 * No native types or registry-dependent attribute holders are persisted here. */
public record SilentGearState(Optional<Oxidation> left, Optional<Oxidation> right,
                              AttributePolicy armorPolicy, List<Projection> armorProjection,
                              boolean armorOverridden, Optional<Sample> sample) {
    public static final Identifier ARMOR = Identifier.withDefaultNamespace("armor");
    public static final Identifier MODIFIER = Identifier.withDefaultNamespace("armor.leggings");
    private static final int MAX_ENTRIES = 16;
    private static final Codec<AttributePolicy> POLICY = Codec.STRING.comapFlatMap(
            value -> AttributePolicy.fromSerializedName(value).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown SG armor policy")), AttributePolicy::serializedName);
    public static final Codec<SilentGearState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Oxidation.CODEC.optionalFieldOf("left").forGetter(SilentGearState::left),
            Oxidation.CODEC.optionalFieldOf("right").forGetter(SilentGearState::right),
            POLICY.fieldOf("armor_policy").forGetter(SilentGearState::armorPolicy),
            Projection.CODEC.listOf(0, MAX_ENTRIES).fieldOf("armor_projection").forGetter(SilentGearState::armorProjection),
            Codec.BOOL.fieldOf("armor_overridden").forGetter(SilentGearState::armorOverridden),
            Sample.CODEC.optionalFieldOf("sample").forGetter(SilentGearState::sample)
    ).apply(instance, SilentGearState::new));

    public SilentGearState {
        armorProjection = List.copyOf(armorProjection);
        if (armorProjection.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Oversized SG armor projection");
        }
        java.util.Objects.requireNonNull(left);
        java.util.Objects.requireNonNull(right);
        java.util.Objects.requireNonNull(armorPolicy);
        java.util.Objects.requireNonNull(sample);
    }

    public static boolean managed(ItemAttributeModifiers.Entry entry) {
        return entry.attribute().unwrapKey().orElseThrow().identifier().equals(ARMOR)
                && entry.modifier().id().equals(MODIFIER) && entry.slot() == EquipmentSlotGroup.LEGS;
    }

    /** The identity is fixed by this schema; amount, operation, display, order and conflicts are retained. */
    public record Projection(AttributeModifier modifier, ItemAttributeModifiers.Display display) {
        private static final Codec<ItemAttributeModifiers.Display> DISPLAY = ItemAttributeModifiers.Display.CODEC.validate(value ->
                boundedDisplay(value) ? DataResult.success(value) : DataResult.error(() -> "Oversized SG armor display"));
        private static final Codec<AttributeModifier> MOD = AttributeModifier.CODEC.validate(value ->
                value.id().equals(MODIFIER) && Double.isFinite(value.amount()) ? DataResult.success(value)
                        : DataResult.error(() -> "Invalid SG managed armor modifier"));
        public static final Codec<Projection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MOD.fieldOf("modifier").forGetter(Projection::modifier),
                DISPLAY.fieldOf("display").forGetter(Projection::display)
        ).apply(instance, Projection::new));

        public Projection {
            if (!modifier.id().equals(MODIFIER) || !Double.isFinite(modifier.amount())) {
                throw new IllegalArgumentException("Invalid SG managed armor modifier");
            }
            if (!boundedDisplay(display)) {
                throw new IllegalArgumentException("Oversized SG armor display");
            }
        }

        private static boolean boundedDisplay(ItemAttributeModifiers.Display value) {
            return ItemAttributeModifiers.Display.CODEC.encodeStart(JsonOps.INSTANCE, value).result()
                    .map(json -> json.toString().length() <= 4096).orElse(false);
        }

        public static List<Projection> capture(ItemAttributeModifiers attributes) {
            return attributes.modifiers().stream().filter(SilentGearState::managed)
                    .map(entry -> new Projection(entry.modifier(), entry.display())).toList();
        }
    }

    public record Oxidation(String phase, int level, int stage, int counter) {
        private static final Codec<String> PHASE = Codec.STRING.validate(value ->
                value.equals("polished") || value.equals("oxidized") ? DataResult.success(value)
                        : DataResult.error(() -> "Unknown SG oxidation phase"));
        public static final Codec<Oxidation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PHASE.fieldOf("phase").forGetter(Oxidation::phase),
                Codec.intRange(1, Byte.MAX_VALUE).fieldOf("level").forGetter(Oxidation::level),
                Codec.intRange(0, Byte.MAX_VALUE).fieldOf("stage").forGetter(Oxidation::stage),
                Codec.intRange(0, Integer.MAX_VALUE - 1).fieldOf("counter").forGetter(Oxidation::counter)
        ).apply(instance, Oxidation::new));

        public Oxidation {
            if ((!phase.equals("polished") && !phase.equals("oxidized")) || level < 1 || level > Byte.MAX_VALUE
                    || stage < 0 || stage > Byte.MAX_VALUE || counter < 0 || counter == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid SG oxidation lane");
            }
        }
    }

    public record Sample(int damage, int maximum, String definition) {
        public static final Codec<Sample> CODEC = RawSample.CODEC.comapFlatMap(value -> {
            if (value.damage < 0 || value.maximum < 0 || value.damage > value.maximum || value.definition.length() > 512) {
                return DataResult.error(() -> "Invalid SG refresh sample");
            }
            return DataResult.success(new Sample(value.damage, value.maximum, value.definition));
        }, value -> new RawSample(value.damage, value.maximum, value.definition));

        public Sample {
            if (damage < 0 || maximum < 0 || damage > maximum || definition.length() > 512) {
                throw new IllegalArgumentException("Invalid SG refresh sample");
            }
        }
    }

    private record RawSample(int damage, int maximum, String definition) {
        private static final Codec<RawSample> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("damage").forGetter(RawSample::damage),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("maximum").forGetter(RawSample::maximum),
                Codec.STRING.validate(value -> value.length() <= 512 ? DataResult.success(value)
                        : DataResult.error(() -> "Oversized SG definition sample"))
                        .fieldOf("definition").forGetter(RawSample::definition)
        ).apply(instance, RawSample::new));
    }
}
