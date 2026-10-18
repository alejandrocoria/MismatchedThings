package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/** Production factory/native component smoke only; no forge, menu, transfer or restart acceptance. */
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
            boolean absent = Boolean.getBoolean("mismatchedboots.runtimetest.neoVitaeAbsent");
            require(ModList.get().isLoaded("neovitae") != absent, "Wrong optional-mod environment");
            if (absent) { verifyAbsent(); }
            else { verifyNative(); }
            NetworkAcceptanceFiles.write("neo-vitae-composition-report.txt", absent
                    ? "optional absent: production pair, inverse provenance, ordinary durability, immutable inputs\n"
                    : "native components + production creation finalizer: one-sided, same-type, conflicting positive types, "
                    + "empty/defaulted, no container/orphan charge; marker union including false; "
                    + "both orientations, swapped inputs, repeated previews, immutable provenance, malformed rejection\n");
            NetworkAcceptanceFiles.write("neo-vitae-composition-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("neo-vitae-composition", failure);
        } finally { server.halt(false); }
    }

    private static void verifyAbsent() {
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
    }

    private static void verifyNative() {
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
    }

    private static void tuple(ItemStack root, DataComponentType<Double> capacity, DataComponentType<Double> amount,
                              DataComponentType<Object> type, double expectedCapacity, double expectedAmount, Object expectedType) {
        require(Double.valueOf(expectedCapacity).equals(root.get(capacity))
                && Double.valueOf(expectedAmount).equals(root.get(amount)) && expectedType.equals(root.get(type)),
                "Unexpected native tuple: " + root);
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(Identifier.fromNamespaceAndPath("neovitae", path)).orElseThrow();
    }

    private static void require(boolean valid, String message) {
        if (!valid) { throw new IllegalStateException(message); }
    }
}
