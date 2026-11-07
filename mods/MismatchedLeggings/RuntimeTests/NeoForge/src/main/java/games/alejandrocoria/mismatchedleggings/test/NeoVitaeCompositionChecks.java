package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Product factory/recomposer wiring with real native codecs; arithmetic edge cases belong to Things' unit suite. */
final class NeoVitaeCompositionChecks {
    private final DataComponentType<Double> capacity = component("spiritus_max");
    private final DataComponentType<Double> amount = component("spiritus_amount");
    private final DataComponentType<Object> type = component("spiritus_type");
    private final DataComponentType<Boolean> blood = component("blood_mending");
    private final Object raw = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("raw")).getOrThrow();
    private final Object ruina = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("ruina")).getOrThrow();

    private NeoVitaeCompositionChecks() {}

    static void verify() throws Exception {
        var fixture = new NeoVitaeCompositionChecks();
        var previousRules = CompositionRuleState.server();
        try {
            fixture.creation();
            fixture.preservation();
            // Deliberately conflicting generic projections must not split the dedicated native tuple.
            var overrides = new ComponentRuleSet(Map.of(
                    id("spiritus_max"), ComponentRule.of(ComponentPolicy.OMIT),
                    id("spiritus_amount"), ComponentRule.of(ComponentPolicy.SHARE),
                    id("spiritus_type"), ComponentRule.of(ComponentPolicy.OMIT),
                    id("blood_mending"), ComponentRule.of(ComponentPolicy.OMIT)));
            CompositionRuleState.installServer(new CompositionRules(previousRules.attributes(), new ComponentRules(
                    previousRules.components().durability(), previousRules.components().repairCost(), overrides)));
            fixture.creation();
            fixture.preservation();
            fixture.malformed();
        } finally {
            CompositionRuleState.installServer(previousRules);
        }
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), "neovitae-composition-server.txt"),
                "Native factory: one-sided, compatible/conflicting positive types, remembered empty/defaulted and orphan fields; "
                        + "marker false/presence union, both input orders/orientations, repeated previews and immutable history\n"
                        + "Recomposer: exact live tuple/marker presence, spent/empty/retyped and root-only state, old-projection equality, "
                        + "synthetic source replacement and repetition; conflicting generic overrides; malformed creation/live refusal\n"
                        + "Not covered here: paid composed-root workstation transactions or separate saved-world reopen\n");
    }

    private void creation() {
        var first = infused(Items.IRON_LEGGINGS, 100, 60, raw);
        first.set(blood, false);
        var plain = new ItemStack(Items.DIAMOND_LEGGINGS);
        var half = new Tuple(100D, 30D, raw);
        pair(first, plain, half, half, true);

        var second = infused(Items.DIAMOND_LEGGINGS, 200, 90, raw);
        var average = new Tuple(200D, 75D, raw);
        pair(first, second, average, average, true);
        second.set(type, ruina);
        pair(first, second, new Tuple(200D, 60D, raw), new Tuple(200D, 90D, ruina), true);

        second.set(amount, 0D);
        var positive = new Tuple(200D, 30D, raw);
        pair(first, second, positive, positive, true);
        first.remove(amount);
        first.remove(type);
        first.remove(blood);
        var empty = new Tuple(200D, 0D, raw);
        pair(first, second, empty, empty, false);
        var defaulted = new Tuple(100D, 0D, raw);
        pair(first, plain, defaulted, defaulted, false);

        var orphan = plain.copy();
        orphan.set(amount, 500D);
        orphan.set(type, ruina);
        var absent = new Tuple(null, null, null);
        pair(orphan, new ItemStack(Items.GOLDEN_LEGGINGS), absent, absent, false);
    }

    private void pair(ItemStack first, ItemStack second, Tuple primary, Tuple inverse, boolean marked) {
        var firstBefore = first.copy();
        var secondBefore = second.copy();
        for (int preview = 0; preview < 2; preview++) {
            for (boolean swap : List.of(false, true)) {
                var left = swap ? second : first;
                var right = swap ? first : second;
                var result = MixedLeggingsFactory.createPair(left, right);
                require(tuple(result.primary()).equals(swap ? inverse : primary)
                                && tuple(result.inverse()).equals(swap ? primary : inverse),
                        "Leggings creation lost the coherent native tuple");
                for (var root : List.of(result.primary(), result.inverse())) {
                    require(marked ? Boolean.TRUE.equals(root.get(blood)) : !root.has(blood),
                            "Leggings creation did not union native marker presence");
                    var history = MixedLeggingsStructure.validData(root).orElseThrow();
                    require(ItemStack.matches(history.left().source(), root == result.primary() ? left : right)
                                    && ItemStack.matches(history.right().source(), root == result.primary() ? right : left),
                            "Creation changed ordered source history");
                }
            }
        }
        require(ItemStack.matches(firstBefore, first) && ItemStack.matches(secondBefore, second), "Creation mutated sources");
    }

    private void preservation() {
        var first = infused(Items.DIAMOND_LEGGINGS, 100, 60, raw);
        first.set(blood, false);
        var second = new ItemStack(Items.IRON_LEGGINGS);
        var pair = MixedLeggingsFactory.createPair(first, second);
        record Example(String name, Consumer<ItemStack> change) {}
        var cases = List.of(
                new Example("spent", root -> root.set(amount, 7.25)),
                new Example("depleted", root -> root.set(amount, 0D)),
                new Example("retyped", root -> root.set(type, ruina)),
                new Example("equal old projection", root -> {
                    root.set(amount, 60D);
                    root.set(blood, false);
                }),
                new Example("larger root-only capacity", root -> {
                    root.set(capacity, 4096D);
                    root.set(amount, 512D);
                    root.set(type, ruina);
                }),
                new Example("removed tuple and marker", root -> {
                    root.remove(capacity);
                    root.remove(amount);
                    root.remove(type);
                    root.remove(blood);
                }),
                new Example("missing native read-default fields", root -> {
                    root.remove(amount);
                    root.remove(type);
                }),
                new Example("orphan fields without capacity", root -> root.remove(capacity)),
                new Example("false marker", root -> root.set(blood, false)));
        for (var orientation : List.of(pair.primary(), pair.inverse())) {
            for (var example : cases) {
                var root = orientation.copy();
                example.change.accept(root);
                var history = MixedLeggingsStructure.validData(root).orElseThrow();
                // Synthetic replacement exposes projection-equality mistakes; native paid recipes are covered separately.
                var changed = infused(history.left().source().getItem(), 1000, 900, ruina);
                changed.set(blood, true);
                preserve(root, changed, history.right().source(), example.name());
            }
        }
        for (boolean install : List.of(false, true)) {
            var plain = MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS), second);
            for (var root : List.of(plain.primary(), plain.inverse())) {
                if (install) {
                    root.set(capacity, 4096D);
                    root.set(amount, 12.5);
                    root.set(type, ruina);
                    root.set(blood, true);
                }
                preserve(root, first, second, "root-only installation/explicit absence");
            }
        }
    }

    private void preserve(ItemStack root, ItemStack left, ItemStack right, String context) {
        var before = root.copy();
        var leftBefore = left.copy();
        var rightBefore = right.copy();
        var output = MixedLeggingsRecomposer.recompose(root, left, right);
        var repeated = MixedLeggingsRecomposer.recompose(output, left, right);
        for (var result : List.of(output, repeated)) {
            require(tuple(before).equals(tuple(result)) && java.util.Objects.equals(before.get(blood), result.get(blood)),
                    "Recomposition replaced exact live state: " + context);
            var history = MixedLeggingsStructure.validData(result).orElseThrow();
            require(ItemStack.matches(history.left().source(), leftBefore) && ItemStack.matches(history.right().source(), rightBefore),
                    "Recomposition lost transformed history: " + context);
        }
        require(ItemStack.matches(before, root) && ItemStack.matches(leftBefore, left) && ItemStack.matches(rightBefore, right),
                "Recomposition mutated inputs: " + context);
    }

    private void malformed() {
        var invalid = infused(Items.DIAMOND_LEGGINGS, 100, 101, raw);
        var plain = new ItemStack(Items.IRON_LEGGINGS);
        var before = invalid.copy();
        var plainBefore = plain.copy();
        for (boolean swap : List.of(false, true)) {
            rejects(() -> MixedLeggingsFactory.createPair(swap ? plain : invalid, swap ? invalid : plain));
        }
        var live = MixedLeggingsFactory.createPair(plain, plain).primary();
        live.set(capacity, 100D);
        live.set(amount, 101D);
        live.set(type, raw);
        var liveBefore = live.copy();
        rejects(() -> MixedLeggingsRecomposer.recompose(live, plain, plain));
        require(ItemStack.matches(invalid, before) && ItemStack.matches(plain, plainBefore) && ItemStack.matches(live, liveBefore),
                "Malformed refusal mutated inputs");
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Malformed native infusion was published");
    }

    private ItemStack infused(Item item, double maximum, double quantity, Object aspect) {
        var source = new ItemStack(item);
        source.set(capacity, maximum);
        source.set(amount, quantity);
        source.set(type, aspect);
        return source;
    }

    private Tuple tuple(ItemStack stack) {
        return new Tuple(stack.get(capacity), stack.get(amount), stack.get(type));
    }

    private record Tuple(Double capacity, Double amount, Object type) {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neovitae", path);
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id(path)).orElseThrow();
    }
}
