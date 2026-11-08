package games.alejandrocoria.mismatchedthings.internal.compatibility.undergarden;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/** Native two-int record, not independent scalar component projections. */
final class RogdoriumInfusionRules {
    private static final Infusion DEFAULT = new Infusion(0, 3600);

    private RogdoriumInfusionRules() {
    }

    static Binding bind(DataComponentType<Object> component) {
        if (component.codec() == null) {
            throw new IllegalArgumentException("Rogdorium infusion is not persistent");
        }
        var binding = new Binding(component);
        // Both native fields must survive decoding: an accepted but lossy codec is not a creation binding.
        create(DEFAULT, binding);
        create(new Infusion(7, 7200), binding);
        return binding;
    }

    static void initializeCreation(ItemStack output, ItemStack owner, ItemStack other, Binding binding) {
        var component = binding.component;
        if (!owner.has(component) && !other.has(component)) {
            output.remove(component);
            return;
        }

        var left = read(owner, binding);
        var right = read(other, binding);
        int amount = (int) (((long) left.amount + right.amount) / 2);
        var result = new Infusion(amount, Math.max(left.maximum, right.maximum));
        Object nativeValue = create(result, binding);
        // Decode and verify the complete result before touching this defensive factory output.
        output.set(component, nativeValue);
    }

    static void preserveState(ItemStack previous, ItemStack output, DataComponentType<Object> component) {
        Object value = previous.get(component);
        // Equality with a source projection is not provenance; absence is also authoritative live state.
        if (value == null) {
            output.remove(component);
        } else {
            output.set(component, value);
        }
    }

    private static Infusion read(ItemStack stack, Binding binding) {
        Object value = stack.get(binding.component);
        return value == null ? DEFAULT : read(value, binding);
    }

    private static Infusion read(Object value, Binding binding) {
        JsonElement encoded = binding.component.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        if (!(encoded instanceof JsonObject fields) || fields.size() != 2) {
            throw new IllegalArgumentException("Unsupported Rogdorium infusion schema");
        }
        int amount = integer(fields, "infusionAmount");
        int maximum = integer(fields, "infusionMax");
        if (maximum <= 0 || amount < 0 || amount > maximum) {
            throw new IllegalArgumentException("Invalid Rogdorium infusion reserve/capacity");
        }
        return new Infusion(amount, maximum);
    }

    private static int integer(JsonObject fields, String name) {
        var value = fields.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Unsupported Rogdorium infusion field: " + name);
        }
        return value.getAsBigDecimal().intValueExact();
    }

    private static Object create(Infusion infusion, Binding binding) {
        var fields = new JsonObject();
        fields.addProperty("infusionAmount", infusion.amount);
        fields.addProperty("infusionMax", infusion.maximum);
        Object result = binding.component.codec().parse(JsonOps.INSTANCE, fields).getOrThrow();
        if (!read(result, binding).equals(infusion)) {
            throw new IllegalArgumentException("Rogdorium infusion codec changed reserve/capacity");
        }
        return result;
    }

    record Binding(DataComponentType<Object> component) {
    }

    private record Infusion(int amount, int maximum) {
    }
}
