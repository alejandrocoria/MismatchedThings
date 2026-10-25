package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/** Nine immutable projections, never another wallet or a live configuration snapshot. */
public record JustDireAttributeProjections(List<ItemAttributeModifiers> states) {
    public static final Codec<JustDireAttributeProjections> CODEC = ItemAttributeModifiers.CODEC.listOf(9, 9)
                                                                                                .validate(states -> states.stream()
                                                                                                                          .allMatch(state -> state.modifiers()
                                                                                                                                                  .size() <= 32
                                                                                                                                  && state.modifiers()
                                                                                                                                          .stream()
                                                                                                                                          .allMatch(
                                                                                                                                                  JustDireAttributeProjections::valid))
                                                                                                                    ? DataResult.success(states)
                                                                                                                    : DataResult.error(() -> "Invalid JDT attribute projections"))
                                                                                                .xmap(JustDireAttributeProjections::new,
                                                                                                      JustDireAttributeProjections::states);

    public JustDireAttributeProjections {
        states = states.stream().map(state -> new ItemAttributeModifiers(List.copyOf(state.modifiers()))).toList();
        if (states.size() != 9 || states.stream().anyMatch(state -> state.modifiers().size() > 32
                || state.modifiers().stream().anyMatch(entry -> !valid(entry)))) {
            throw new IllegalArgumentException("Invalid bounded JDT attribute projections");
        }
    }

    private static boolean valid(ItemAttributeModifiers.Entry entry) {
        return Double.isFinite(entry.modifier().amount()) && managed(entry);
    }

    static boolean managed(ItemAttributeModifiers.Entry entry) {
        Key key = key(entry);
        return key.modifier.equals(Identifier.withDefaultNamespace("armor.boots"))
                && List.of("armor", "armor_toughness", "knockback_resistance").contains(key.attribute.getPath())
                && key.attribute.getNamespace().equals("minecraft")
                || key.attribute.equals(Identifier.withDefaultNamespace("step_height"))
                && key.modifier.equals(Identifier.fromNamespaceAndPath("justdirethings", "justdirestepassist"));
    }

    static Key key(ItemAttributeModifiers.Entry entry) {
        return new Key(BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()), entry.modifier().id());
    }

    record Key(Identifier attribute, Identifier modifier) {
    }
}
