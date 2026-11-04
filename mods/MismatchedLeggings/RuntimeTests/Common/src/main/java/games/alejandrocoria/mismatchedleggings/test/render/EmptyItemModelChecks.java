package games.alejandrocoria.mismatchedleggings.test.render;

import games.alejandrocoria.mismatchedleggings.mixin.client.ItemStackRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Requires complete diagnostic rendering without changing valid source data. */
public final class EmptyItemModelChecks {
    private static final EnumSet<ItemDisplayContext> BOOTS_SUBMITTED = EnumSet.noneOf(ItemDisplayContext.class);
    private static final EnumSet<ItemDisplayContext> LEGGINGS_SUBMITTED = EnumSet.noneOf(ItemDisplayContext.class);
    private static List<Sample> samples = List.of();

    private EmptyItemModelChecks() {
    }

    public static void run(Minecraft client) {
        require(Boolean.getBoolean("leggings.test.boots") && !Boolean.getBoolean("leggings.test.previousBoots"),
                "Empty-model comparison requires both current products");
        if (samples.isEmpty()) {
            var created = new ArrayList<Sample>();
            for (boolean boots : List.of(true, false)) {
                created.add(sample(boots, "normal", "red", "blue"));
                created.add(sample(boots, "empty-left", "empty", "red"));
                created.add(sample(boots, "empty-right", "red", "empty"));
                created.add(sample(boots, "both-empty", "empty", "empty"));
            }
            samples = List.copyOf(created);
        }
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            var empty = ItemChecks.resolve(source(true, "empty"), context);
            require(empty.isEmpty() && ((ItemStackRenderStateAccessor) (Object) empty).mismatchedleggings$getActiveLayerCount() == 0,
                    "Empty fixture resolved to a missing-texture model instead of zero layers");
            for (var sample : samples) {
                var state = ItemChecks.resolve(sample.root(), context);
                var faces = ItemChecks.faces(state);
                var diagnostic = ItemChecks.faces(ItemChecks.resolve(new ItemStack(sample.root().getItem()), context));
                require(!diagnostic.isEmpty(), "Bare diagnostic control is unexpectedly invisible");
                boolean red = faces.stream().anyMatch(face -> face.texture().contains("redstone_block"));
                boolean blue = faces.stream().anyMatch(face -> face.texture().contains("lapis_block"));
                boolean completeDiagnostic = faces.equals(diagnostic);
                if (sample.label().equals("normal")) {
                    require(red && blue, "Normal two-source item control lost its geometry");
                } else {
                    require(completeDiagnostic, "Empty-source pair did not select its complete diagnostic fallback: " + sample);
                    checkFoil(sample, context);
                }
                if (context == ItemDisplayContext.FIXED) {
                    checkAppendedLayers(client, sample, faces);
                }
                System.out.println("EMPTY_ITEM product=" + (sample.boots() ? "boots" : "leggings") + " case=" + sample.label()
                        + " context=" + context + " empty=" + state.isEmpty() + " faces=" + faces.size()
                        + " diagnostic=" + completeDiagnostic + " red=" + red + " blue=" + blue);
            }
        }
    }

    public static List<Sample> samples() {
        return samples;
    }

    private static void checkFoil(Sample sample, ItemDisplayContext context) {
        var before = sample.root().copy();
        for (boolean foil : List.of(false, true)) {
            var root = sample.root().copy();
            root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
            var diagnostic = new ItemStack(root.getItem());
            diagnostic.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
            var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
            require(faces.equals(ItemChecks.faces(ItemChecks.resolve(diagnostic, context))),
                    "Diagnostic fallback lost geometry or effective root foil: " + sample);
            require(faces.stream().allMatch(face -> face.foil() == foil), "Diagnostic foil does not match the root");
            var expected = before.copy();
            expected.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
            require(ItemStack.matches(root, expected), "Rendering changed root components or source provenance");
        }
        require(ItemStack.matches(sample.root(), before), "Rendering changed the original pair");
    }

    private static void checkAppendedLayers(Minecraft client, Sample sample, List<ItemChecks.Face> expectedFaces) {
        var output = ItemChecks.resolve(new ItemStack(Items.DIAMOND), ItemDisplayContext.FIXED);
        var prefixFaces = ItemChecks.faces(output);
        require(!prefixFaces.isEmpty(), "Prefix-layer control is unexpectedly invisible");
        // Populate the native bounds cache before replacing only this pair's newly appended ranges.
        output.getModelBoundingBox();
        client.getItemModelResolver().appendItemLayers(output, sample.root(), ItemDisplayContext.FIXED,
                client.level, client.player, 0);
        var combined = ItemChecks.faces(output);
        var expected = new ArrayList<>(prefixFaces);
        expected.addAll(expectedFaces);
        require(combined.equals(expected), "Mixed item rendering removed or altered preceding layers: " + sample);
    }

    private static Sample sample(boolean boots, String label, String left, String right) {
        var first = source(boots, left);
        var second = source(boots, right);
        if (!boots) {
            return new Sample(false, label, RenderFixture.pair(first, second));
        }
        try {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, first, second);
            return new Sample(true, label, (ItemStack) pair.getClass().getMethod("primary").invoke(pair));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot construct optional Boots empty-model sample", failure);
        }
    }

    private static ItemStack source(boolean boots, String model) {
        var source = new ItemStack(boots ? Items.IRON_BOOTS : Items.IRON_LEGGINGS);
        source.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("leggings_test", model));
        return source;
    }

    public static void resetSubmissions() {
        BOOTS_SUBMITTED.clear();
        LEGGINGS_SUBMITTED.clear();
    }

    public static void observe(ItemDisplayContext context, List<ItemChecks.Face> faces) {
        if (faces.isEmpty()) {
            return;
        }
        if (faces.stream().anyMatch(face -> face.texture().contains("iron_boots"))
                && faces.stream().anyMatch(face -> face.texture().contains("golden_boots"))) {
            BOOTS_SUBMITTED.add(context);
        }
        if (faces.stream().anyMatch(face -> face.texture().contains("iron_leggings"))
                && faces.stream().anyMatch(face -> face.texture().contains("golden_leggings"))) {
            LEGGINGS_SUBMITTED.add(context);
        }
    }

    public static boolean thirdPersonComplete() {
        return submitted(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }

    public static boolean firstPersonComplete() {
        return submitted(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }

    private static boolean submitted(ItemDisplayContext left, ItemDisplayContext right) {
        return BOOTS_SUBMITTED.containsAll(List.of(left, right)) && LEGGINGS_SUBMITTED.containsAll(List.of(left, right));
    }

    public static void hold(Minecraft client, int ticks) {
        int first = ticks % 50 < 25 ? 1 : 5;
        client.player.setItemInHand(InteractionHand.MAIN_HAND, samples.get(first).root());
        client.player.setItemInHand(InteractionHand.OFF_HAND, samples.get(first + 1).root());
    }

    public record Sample(boolean boots, String label, ItemStack root) {
    }
}
