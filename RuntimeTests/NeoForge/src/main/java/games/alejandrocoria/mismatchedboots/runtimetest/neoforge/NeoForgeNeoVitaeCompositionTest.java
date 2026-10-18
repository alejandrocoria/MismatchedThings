package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Focused creation/recomposition rules plus native lifecycle acceptance in two game processes. */
public final class NeoForgeNeoVitaeCompositionTest {
    private static boolean finished;

    private NeoForgeNeoVitaeCompositionTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            if (Boolean.getBoolean("mismatchedboots.runtimetest.neoVitaeRestart")) {
                NeoVitaeCompositionLifecycleTest.restart(server);
                NetworkAcceptanceFiles.write("neo-vitae-composition-restart-success.txt", "neoforge\n");
                return;
            }
            boolean absent = Boolean.getBoolean("mismatchedboots.runtimetest.neoVitaeAbsent");
            require(ModList.get().isLoaded("neovitae") != absent, "Wrong optional-mod environment");
            if (absent) { verifyAbsent(server.overworld()); }
            else {
                verifyNative(server.overworld());
                NeoVitaeCompositionLifecycleTest.save(server);
            }
            NetworkAcceptanceFiles.write("neo-vitae-composition-report.txt", absent
                    ? "optional absent: production pair, inverse provenance, ordinary durability, immutable inputs, tier recomposition preview\n"
                    : "native components + production creation finalizer: one-sided, same-type, conflicting positive types, "
                    + "empty/defaulted, no container/orphan charge; marker union including false; "
                    + "both orientations, swapped inputs, repeated previews, immutable provenance, malformed rejection; "
                    + "live tuple/marker preservation: 8 states x 2 orientations with synthetic source replacement and repeat; "
                    + "root-only installation/absence, invalid live rejection; paid native menus covered by lifecycle report\n");
            NetworkAcceptanceFiles.write("neo-vitae-composition-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(Boolean.getBoolean("mismatchedboots.runtimetest.neoVitaeRestart")
                    ? "neo-vitae-composition-restart" : "neo-vitae-composition", failure);
        } finally { server.halt(false); }
    }

    private static void verifyAbsent(ServerLevel level) {
        ItemStack first = new ItemStack(Items.IRON_BOOTS), second = new ItemStack(Items.DIAMOND_BOOTS);
        ItemStack before = first.copy(), secondBefore = second.copy();
        var pair = MixedBootsFactory.createPair(first, second);
        int expectedDurability = (first.getMaxDamage() + second.getMaxDamage()) / 2;
        require(pair.primary().getMaxDamage() == expectedDurability
                && pair.inverse().getMaxDamage() == expectedDurability, "Ordinary average durability changed");
        require(ItemStack.matches(ModContent.getMixedBootsData(pair.inverse()).orElseThrow().left().source(), second),
                "Absent inverse provenance changed");
        require(ItemStack.matches(before, first) && ItemStack.matches(secondBefore, second), "Absent source mutated");
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            require(root.getComponents().keySet().stream().noneMatch(type -> {
                Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                return id != null && id.getNamespace().equals("neovitae");
            }), "Absent integration manufactured components");
        }
        ItemStack tier = smithing(level, pair.inverse(), Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.NETHERITE_INGOT);
        require(ModContent.getMixedBootsData(tier).orElseThrow().left().source().is(Items.NETHERITE_BOOTS),
                "Absent tier recomposition failed");
    }

    private static void verifyNative(ServerLevel level) {
        DataComponentType<Double> capacity = component("spiritus_max"), amount = component("spiritus_amount");
        DataComponentType<Object> type = component("spiritus_type");
        DataComponentType<Boolean> blood = component("blood_mending");
        Object raw = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("raw")).getOrThrow();
        Object ruina = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("ruina")).getOrThrow();
        ItemStack first = new ItemStack(Items.IRON_BOOTS), plain = new ItemStack(Items.DIAMOND_BOOTS);
        first.set(capacity, 100D); first.set(amount, 60D); first.set(type, ruina); first.set(blood, false);
        ItemStack before = first.copy(), plainBefore = plain.copy();
        for (int preview = 0; preview < 2; preview++) {
            for (boolean swap : List.of(false, true)) {
                var pair = MixedBootsFactory.createPair(swap ? plain : first, swap ? first : plain);
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    tuple(root, capacity, amount, type, 100, 30, ruina);
                    require(Boolean.TRUE.equals(root.get(blood)), "Presence-based marker union failed");
                    var data = ModContent.getMixedBootsData(root).orElseThrow();
                    require(ItemStack.matches(data.left().source(), root == pair.primary()
                            ? (swap ? plainBefore : before) : (swap ? before : plainBefore)), "Owner provenance changed");
                    require(ItemStack.matches(data.right().source(), root == pair.primary()
                            ? (swap ? before : plainBefore) : (swap ? plainBefore : before)), "Other provenance changed");
                }
            }
        }
        require(ItemStack.matches(before, first) && ItemStack.matches(plainBefore, plain), "Preview mutated inputs");

        ItemStack second = plain.copy();
        second.set(capacity, 200D); second.set(amount, 90D); second.set(type, raw);
        var conflict = MixedBootsFactory.createPair(first, second);
        tuple(conflict.primary(), capacity, amount, type, 200, 60, ruina);
        tuple(conflict.inverse(), capacity, amount, type, 200, 90, raw);
        var swapped = MixedBootsFactory.createPair(second, first);
        // Swapping inputs can reorder unrelated projections (repair ingredients); compare the owned tuple.
        tuple(swapped.primary(), capacity, amount, type, 200, 90, raw);
        tuple(swapped.inverse(), capacity, amount, type, 200, 60, ruina);
        second.set(type, ruina);
        var compatible = MixedBootsFactory.createPair(first, second);
        for (ItemStack root : List.of(compatible.primary(), compatible.inverse())) {
            tuple(root, capacity, amount, type, 200, 75, ruina);
        }
        first.remove(amount); first.remove(type); first.remove(blood);
        var empty = MixedBootsFactory.createPair(first, plain);
        for (ItemStack root : List.of(empty.primary(), empty.inverse())) {
            tuple(root, capacity, amount, type, 100, 0, raw);
            require(!root.has(blood), "Absent marker manufactured");
        }
        plain.set(amount, 500D); plain.set(type, ruina);
        var uninfused = MixedBootsFactory.createPair(plain, new ItemStack(Items.GOLDEN_BOOTS));
        for (ItemStack root : List.of(uninfused.primary(), uninfused.inverse())) {
            require(!root.has(capacity) && !root.has(amount) && !root.has(type), "Orphan charge installed container");
        }
        first.set(amount, 101D);
        ItemStack invalidBefore = first.copy(), otherBefore = plain.copy();
        boolean rejected = false;
        try { MixedBootsFactory.createPair(plain, first); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && ItemStack.matches(first, invalidBefore) && ItemStack.matches(plain, otherBefore),
                "Malformed pair was published or consumed inputs");
        verifyPreservation(level, capacity, amount, type, blood, raw, ruina);
    }

    private static void verifyPreservation(ServerLevel level, DataComponentType<Double> capacity,
                                           DataComponentType<Double> amount, DataComponentType<Object> type,
                                           DataComponentType<Boolean> blood, Object raw, Object ruina) {
        ItemStack first = new ItemStack(Items.DIAMOND_BOOTS), second = new ItemStack(Items.IRON_BOOTS);
        first.set(capacity, 100D); first.set(amount, 60D); first.set(type, raw); first.set(blood, false);
        var pair = MixedBootsFactory.createPair(first, second);
        record Case(String name, Consumer<ItemStack> change) {}
        var cases = List.of(
                new Case("spent", root -> root.set(amount, 7.25)),
                new Case("depleted", root -> root.set(amount, 0D)),
                new Case("refilled different type", root -> { root.set(amount, 12.5); root.set(type, ruina); }),
                new Case("equal old projection", root -> { root.set(amount, 60D); root.set(blood, false); }),
                new Case("larger live container", root -> { root.set(capacity, 4096D); root.set(amount, 512D); root.set(type, ruina); }),
                new Case("removed tuple/marker", root -> { root.remove(capacity); root.remove(amount); root.remove(type); root.remove(blood); }),
                new Case("defaulted fields", root -> { root.remove(amount); root.remove(type); }),
                new Case("present false marker", root -> root.set(blood, false)));
        for (ItemStack orientation : List.of(pair.primary(), pair.inverse())) {
            for (Case example : cases) {
                ItemStack root = orientation.copy();
                decorate(level, root);
                example.change.accept(root);
                ItemStack before = root.copy();
                var data = ModContent.getMixedBootsData(root).orElseThrow();
                ItemStack changed = data.left().source(), unchanged = data.right().source();
                // Deliberate synthetic source replacement, not a native recipe: expose three-way equality mistakes.
                changed.set(capacity, 200D); changed.set(amount, 90D); changed.set(type, ruina); changed.set(blood, true);
                ItemStack changedBefore = changed.copy(), unchangedBefore = unchanged.copy();
                ItemStack output = MixedBootsRecomposer.recompose(root, changed, unchanged);
                sameLiveState(root, output, capacity, amount, type, blood, example.name);
                sameLiveState(root, MixedBootsRecomposer.recompose(output, changed, unchanged),
                        capacity, amount, type, blood, example.name + " repeated");
                var projected = ModContent.getMixedBootsData(output).orElseThrow();
                require(ItemStack.matches(projected.left().source(), changedBefore)
                        && ItemStack.matches(projected.right().source(), unchangedBefore), "Transformed provenance mismatch");
                require(ItemStack.matches(before, root) && ItemStack.matches(changedBefore, changed)
                        && ItemStack.matches(unchangedBefore, unchanged), "Recomposition mutated an input");
            }
        }
        // Neither historical source had storage: preserve a later root-only install, or exact absence.
        for (boolean installed : List.of(false, true)) {
            ItemStack root = MixedBootsFactory.createPair(new ItemStack(Items.DIAMOND_BOOTS), second).primary();
            decorate(level, root);
            if (installed) {
                root.set(capacity, 4096D); root.set(amount, 512D); root.set(type, ruina); root.set(blood, true);
            }
            sameLiveState(root, MixedBootsRecomposer.recompose(root, first, second),
                    capacity, amount, type, blood, "root-only installation/absence");
        }

        ItemStack live = pair.primary().copy();
        decorate(level, live);
        live.set(capacity, 4096D); live.set(amount, 12.5); live.set(type, ruina); live.set(blood, false);
        // C's paid native menu takes replace the former duplicate positive recipe previews.
        live.set(amount, 4097D);
        ItemStack invalidBefore = live.copy();
        boolean rejected = false;
        try { MixedBootsRecomposer.recompose(live, first, second); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && MixedBootsSmithingPolicy.evaluate(level,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), live, new ItemStack(Items.NETHERITE_INGOT)).isEmpty()
                && ItemStack.matches(invalidBefore, live), "Invalid live tuple was published or mutated");
    }

    static void decorate(ServerLevel level, ItemStack root) {
        root.setDamageValue(7);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Live infusion"));
        root.set(DataComponents.REPAIR_COST, 11);
        root.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
    }

    private static ItemStack smithing(ServerLevel level, ItemStack root, Item template, Item addition) {
        ItemStack before = root.copy();
        ItemStack result = MixedBootsSmithingPolicy.evaluate(level, new ItemStack(template), root, new ItemStack(addition))
                .orElseThrow(() -> new IllegalStateException("Missing loaded smithing preview: " + template)).output();
        require(ItemStack.matches(before, root), "Recipe preview mutated root/provenance");
        return result;
    }

    static void sameLiveState(ItemStack previous, ItemStack output, DataComponentType<Double> capacity,
                                       DataComponentType<Double> amount, DataComponentType<Object> type,
                                       DataComponentType<Boolean> blood, String context) {
        for (var field : List.of(capacity, amount, type, blood, DataComponents.DAMAGE, DataComponents.CUSTOM_NAME,
                DataComponents.ENCHANTMENTS, DataComponents.REPAIR_COST)) {
            require(previous.has(field) == output.has(field) && Objects.equals(previous.get(field), output.get(field)),
                    "Live state changed during " + context + ": " + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(field));
        }
    }

    private static void tuple(ItemStack root, DataComponentType<Double> capacity, DataComponentType<Double> amount,
                              DataComponentType<Object> type, double expectedCapacity, double expectedAmount, Object expectedType) {
        require(Double.valueOf(expectedCapacity).equals(root.get(capacity))
                && Double.valueOf(expectedAmount).equals(root.get(amount)) && expectedType.equals(root.get(type)),
                "Unexpected native tuple: " + root);
    }

    @SuppressWarnings("unchecked")
    static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(Identifier.fromNamespaceAndPath("neovitae", path)).orElseThrow();
    }

    private static void require(boolean valid, String message) {
        if (!valid) { throw new IllegalStateException(message); }
    }
}
