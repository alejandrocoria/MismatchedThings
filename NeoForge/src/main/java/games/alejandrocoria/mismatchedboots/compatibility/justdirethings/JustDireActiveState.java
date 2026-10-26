package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Strict paid state, with one bounded exception for the other supported action's worn clock. */
public final class JustDireActiveState {
    public static final int GROUND_STOMP = 1;
    public static final int EARTHQUAKE = 2;
    private static Access access;
    private static boolean attempted;

    private JustDireActiveState() {
    }

    public static ItemStack snapshot(ItemStack root) {
        var copy = root.copy();
        // Native records are immutable, but their lists can be edited in place by helpers.
        // Detach those lists so a live edit cannot rewrite a previously issued permission.
        for (String name : List.of("ability_bindings", "ability_cooldowns")) {
            DataComponentType<List<Object>> type = component(name);
            if (type != null && copy.has(type)) {
                copy.set(type, List.copyOf(copy.get(type)));
            }
        }
        return copy;
    }

    public static boolean paidMatches(ItemStack root, ItemStack expected, int actions, long maximumTicks) {
        if (actions <= 0 || (actions & ~(GROUND_STOMP | EARTHQUAKE)) != 0) {
            return false;
        }
        if (ItemStack.matches(root, expected)) {
            return true;
        }

        DataComponentType<List<Object>> type = component("ability_cooldowns");
        if (type == null || root.has(type) != expected.has(type) || maximumTicks < 0) {
            return false;
        }

        var actual = root.copy();
        var strict = expected.copy();
        actual.remove(type);
        strict.remove(type);
        if (!ItemStack.matches(actual, strict)) {
            return false;
        }

        Access binding = binding();
        if (binding == null) {
            return false;
        }

        try {
            var before = expected.getOrDefault(type, List.of());
            var after = root.getOrDefault(type, List.of());
            var fixedBefore = new ArrayList<Object>();
            var fixedAfter = new ArrayList<Object>();
            for (Object record : before) {
                if (!unrelated(binding.name(record), actions)) {
                    fixedBefore.add(record);
                }
            }
            for (Object record : after) {
                if (!unrelated(binding.name(record), actions)) {
                    fixedAfter.add(record);
                }
            }
            if (!fixedBefore.equals(fixedAfter)) {
                return false;
            }

            var replacements = new HashMap<String, Object>();
            for (String name : List.of("groundstomp", "earthquake")) {
                if (!unrelated(name, actions)) {
                    continue;
                }

                var oldRecords = new ArrayList<Object>();
                var newRecords = new ArrayList<Object>();
                for (Object record : before) {
                    if (binding.name(record).equals(name)) {
                        oldRecords.add(record);
                    }
                }
                for (Object record : after) {
                    if (binding.name(record).equals(name)) {
                        newRecords.add(record);
                    }
                }
                if (oldRecords.equals(newRecords)) {
                    continue;
                }
                if (oldRecords.size() != 1 || newRecords.size() > 1) {
                    return false;
                }

                Clock oldClock = binding.clock(oldRecords.getFirst());
                Clock newClock = newRecords.isEmpty() ? null : binding.clock(newRecords.getFirst());
                int cooldown = name.equals("earthquake") ? JustDireEarthquakeCompatibility.cooldownDuration() : 0;
                if (!oldClock.progressesTo(newClock, cooldown, maximumTicks)) {
                    return false;
                }
                replacements.put(name, newRecords.isEmpty() ? null : newRecords.getFirst());
            }

            var progressed = new ArrayList<Object>();
            for (Object record : before) {
                String name = binding.name(record);
                Object replacement = replacements.containsKey(name) ? replacements.get(name) : record;
                if (replacement != null) {
                    progressed.add(replacement);
                }
            }

            return progressed.equals(after); // Owned ticking retains record order, except actual expiry.
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }
            return false; // A malformed comparison never repairs data or grants a paid action.
        }
    }

    private static boolean unrelated(String name, int actions) {
        return name.equals("groundstomp") && (actions & GROUND_STOMP) == 0
                || name.equals("earthquake") && (actions & EARTHQUAKE) == 0;
    }

    record Clock(int ticks, boolean active) {
        boolean progressesTo(Clock after, int cooldown, long maximumTicks) {
            if (cooldown < 0 || ticks < 0 || active && cooldown <= 0 || cooldown > 0 && ticks == 0) {
                return false;
            }
            long beforeRemaining = (long) ticks + (active ? cooldown : 0);
            if (after == null) {
                return beforeRemaining <= maximumTicks;
            }
            if (after.ticks <= 0 || !active && after.active || active && !after.active && after.ticks > cooldown) {
                return false;
            }
            long difference = beforeRemaining - ((long) after.ticks + (after.active ? cooldown : 0));
            return difference > 0 && difference <= maximumTicks;
        }
    }

    private static synchronized Access binding() {
        if (attempted) {
            return access;
        }
        attempted = true;
        try {
            var timer = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
            var lookup = MethodHandles.publicLookup();
            access = new Access(lookup.findVirtual(timer, "abilityName", MethodType.methodType(String.class))
                                      .asType(MethodType.methodType(String.class, Object.class)),
                                lookup.findVirtual(timer, "cooldownTicks", MethodType.methodType(int.class))
                                      .asType(MethodType.methodType(int.class, Object.class)),
                                lookup.findVirtual(timer, "isactive", MethodType.methodType(boolean.class))
                                      .asType(MethodType.methodType(boolean.class, Object.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things paid clock comparison; changed paid snapshots denied", failure);
        }

        return access;
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String name) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", name))
                                                                           .map(holder -> holder.value()).orElse(null);
    }

    private record Access(MethodHandle name, MethodHandle ticks, MethodHandle active) {
        String name(Object record) throws Throwable {
            return (String) name.invokeExact(record);
        }

        Clock clock(Object record) throws Throwable {
            return new Clock((int) ticks.invokeExact(record), (boolean) active.invokeExact(record));
        }
    }
}
