package games.alejandrocoria.mismatchedboots.compatibility.neovitae;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.math.BigDecimal;

/** Native infusion creation and live-root preservation; snapshots are never resource wallets. */
public final class NeoVitaeInfusionCompatibility {
    private static volatile Binding binding;

    private NeoVitaeInfusionCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("neovitae")) { bus.addListener(NeoVitaeInfusionCompatibility::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var capacity = component("spiritus_max", Double.class, new JsonPrimitive(1D));
            var amount = component("spiritus_amount", Double.class, new JsonPrimitive(0D));
            var type = component("spiritus_type", Object.class, new JsonPrimitive("raw"));
            var bloodMending = component("blood_mending", Boolean.class, new JsonPrimitive(true));
            Object raw = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("raw")).getOrThrow();
            if (!(raw instanceof Enum<?> rawType)) { throw new IllegalStateException("Unsupported Spiritus type schema"); }
            binding = new Binding(capacity, amount, type, bloodMending, rawType);
        } catch (RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Neo Vitae infusion components; mixed infusion composition disabled", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path, Class<T> valueClass, JsonPrimitive sample) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("neovitae", path))
                .orElseThrow(() -> new IllegalStateException("Missing Neo Vitae component: " + path));
        if (type.codec() == null || !valueClass.isInstance(type.codec().parse(JsonOps.INSTANCE, sample).getOrThrow())) {
            throw new IllegalStateException("Unsupported Neo Vitae component: " + path);
        }
        return (DataComponentType<T>) type;
    }

    public static void initializeCreation(ItemStack output) {
        Binding api = binding;
        if (api == null) { return; }
        var data = ModContent.getMixedBootsData(output).orElse(null);
        if (data != null) { initializeCreation(output, data.left().source(), data.right().source(), api); }
    }

    public static void preserveState(ItemStack previous, ItemStack output) {
        Binding api = binding;
        if (api != null) { preserveState(previous, output, api); }
    }

    static void preserveState(ItemStack previous, ItemStack output, Binding api) {
        read(previous, api); // Validate an installed container before writing any part of the live state.
        Double capacity = previous.get(api.capacity);
        Double amount = previous.get(api.amount);
        Object type = previous.get(api.type);
        Boolean bloodMending = previous.get(api.bloodMending);

        // Equality with old projection is not provenance. Keep exact presence; never apply read defaults here.
        restore(output, api.capacity, capacity);
        restore(output, api.amount, amount);
        restore(output, api.type, type);
        restore(output, api.bloodMending, bloodMending);
    }

    private static <T> void restore(ItemStack output, DataComponentType<T> type, T value) {
        if (value == null) { output.remove(type); }
        else { output.set(type, value); }
    }

    // Owner is the left snapshot in this orientation, not a mutable live wallet.
    static void initializeCreation(ItemStack output, ItemStack owner, ItemStack other, Binding api) {
        Infusion left = read(owner, api);
        Infusion right = read(other, api);
        double capacity = Math.max(left.capacity, right.capacity);
        boolean bloodMending = owner.has(api.bloodMending) || other.has(api.bloodMending);
        boolean conflict = left.amount > 0 && right.amount > 0 && !left.type.equals(right.type);
        Object type = conflict ? left.type : left.amount > 0 ? left.type : right.amount > 0 ? right.type : api.raw;
        double amount = conflict ? left.amount : conservativeAverage(left.amount, right.amount);

        // Resolve/validate the entire tuple before touching this defensive factory output.
        if (capacity > 0) {
            output.set(api.capacity, capacity);
            output.set(api.amount, amount);
            output.set(api.type, type);
        } else {
            output.remove(api.capacity);
            output.remove(api.amount);
            output.remove(api.type);
        }
        if (bloodMending) { output.set(api.bloodMending, true); }
        else { output.remove(api.bloodMending); }
    }

    private static Infusion read(ItemStack source, Binding api) {
        Double capacity = source.get(api.capacity);
        if (capacity == null) { return new Infusion(0, 0, api.raw); }
        double amount = source.getOrDefault(api.amount, 0D);
        Object type = source.getOrDefault(api.type, api.raw);
        if (!Double.isFinite(capacity) || capacity <= 0 || !Double.isFinite(amount) || amount < 0 || amount > capacity
                || !(type instanceof Enum<?> value) || value.getDeclaringClass() != api.raw.getDeclaringClass()) {
            throw new IllegalArgumentException("Invalid Neo Vitae infusion");
        }
        return new Infusion(capacity, amount, type);
    }

    private static double conservativeAverage(double first, double second) {
        // Exact binary inputs avoid sum overflow and upward rounding that could mint tiny amounts.
        BigDecimal exact = new BigDecimal(first).add(new BigDecimal(second)).divide(BigDecimal.valueOf(2));
        double result = exact.doubleValue();
        return new BigDecimal(result).compareTo(exact) > 0 ? Math.nextDown(result) : result;
    }

    record Binding(DataComponentType<Double> capacity, DataComponentType<Double> amount,
                   DataComponentType<Object> type, DataComponentType<Boolean> bloodMending, Enum<?> raw) {}

    private record Infusion(double capacity, double amount, Object type) {}
}
