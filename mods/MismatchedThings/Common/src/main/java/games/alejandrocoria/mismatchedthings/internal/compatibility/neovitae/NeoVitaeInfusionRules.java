package games.alejandrocoria.mismatchedthings.internal.compatibility.neovitae;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

/** The four native fields form one container, not independent numeric projections. */
final class NeoVitaeInfusionRules {
    private NeoVitaeInfusionRules() {
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
        if (value == null) {
            output.remove(type);
        } else {
            output.set(type, value);
        }
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
        if (bloodMending) {
            output.set(api.bloodMending, true);
        } else {
            output.remove(api.bloodMending);
        }
    }

    private static Infusion read(ItemStack source, Binding api) {
        Double capacity = source.get(api.capacity);
        if (capacity == null) {
            return new Infusion(0, 0, api.raw);
        }
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
                   DataComponentType<Object> type, DataComponentType<Boolean> bloodMending, Enum<?> raw) {
    }

    private record Infusion(double capacity, double amount, Object type) {
    }
}
