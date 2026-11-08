package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stateless source entitlement view; native MA retains all player effect/cache ownership. */
public final class MysticalAugmentsCompatibility {
    static final String PREFIX = "com.blakebr0.mysticalagriculture.";
    static final String TICK_PROOF = "mismatchedleggings$maArmorTickReady";
    static final String EQUIPMENT_PROOF = "mismatchedleggings$maEquipmentReady";
    static final String HEALTH_PROOF = "mismatchedleggings$maHealthCleanupReady";
    private static final Set<MysticalAugments.Family> DISABLED = ConcurrentHashMap.newKeySet();
    private static volatile Binding binding;
    private static volatile boolean attempted;

    private MysticalAugmentsCompatibility() {
    }

    public static List<Identifier> selectedIds(ItemStack root) {
        return selected(root).stream().map(MysticalAugments.Spec::id).toList();
    }

    private static boolean nativeLeggings(ItemStack source) {
        Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.getNamespace().equals("mysticalagriculture") && MysticalAugments.LEGGINGS.contains(id.getPath());
    }

    private static List<MysticalAugments.Spec> selected(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null || !(nativeLeggings(data.left().source()) || nativeLeggings(data.right().source()))) {
            return List.of();
        }
        Binding api = bindings();
        if (api == null) {
            return List.of();
        }
        return MysticalAugments.select(installed(data.left().source(), api), installed(data.right().source(), api))
                               .stream().filter(spec -> available(spec.family(), api)).toList();
    }

    /** Physical occupied entries are not deduplicated here, independently of effect readiness. */
    private static List<MysticalAugments.Spec> installed(ItemStack source, Binding api) {
        if (!nativeLeggings(source) || source.getItem().getClass() != api.leggingsType) {
            return List.of();
        }
        try {
            int slots = (int) api.slots.invoke(source.getItem());
            int tier = (int) api.tier.invoke(source.getItem());
            String path = BuiltInRegistries.ITEM.getKey(source.getItem()).getPath();
            int expectedTier = switch (path) {
                case "inferium_leggings" -> 1;
                case "prudentium_leggings" -> 2;
                case "tertium_leggings" -> 3;
                case "imperium_leggings" -> 4;
                default -> 5;
            };
            if (slots != (path.equals("awakened_supremium_leggings") ? 2 : 1) || tier != expectedTier) {
                return List.of();
            }
            Object component = source.get(api.component);
            if (!(component instanceof List<?> entries) || entries.size() > slots) {
                return List.of();
            }
            boolean[] occupied = new boolean[slots];
            var result = new ArrayList<MysticalAugments.Spec>();
            for (Object entry : entries) {
                if (entry == null || entry.getClass() != api.entryType) {
                    return List.of();
                }
                int slot = (int) api.slot.invoke(entry);
                if (slot < 0 || slot >= slots || occupied[slot]) {
                    return List.of();
                }
                occupied[slot] = true;
                Identifier id = (Identifier) api.id.invoke(entry);
                MysticalAugments.Spec spec = MysticalAugments.CATALOG.get(id);
                if (spec == null || spec.requiredTier() > tier) {
                    continue;
                }
                Object augment = api.augments.get(id);
                if (augment != null && Boolean.TRUE.equals(api.canApply.invoke(source.getItem(), augment))) {
                    result.add(spec);
                }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return List.of(); // A malformed/foreign source is not authorization; never repair its data here.
        }
    }

    public static List<Object> equipment(Player player, ItemStack stack, List<Object> ordinary) {
        if (player.getItemBySlot(EquipmentSlot.LEGS) != stack) {
            return ordinary;
        }
        List<MysticalAugments.Spec> specs = selected(stack);
        if (specs.isEmpty()) {
            return ordinary;
        }
        var result = new ArrayList<>(ordinary);
        for (var spec : specs) {
            result.add(binding.augments.get(spec.id()));
        }
        return result;
    }

    /** Capture authorization while equipped: by removal time the root may already be broken/absent. */
    public static void refreshHealthCleanup(Object cache, Object augment, Player player) {
        var specs = selected(player.getItemBySlot(EquipmentSlot.LEGS));
        Binding api = binding;
        if (api == null || api.health == null
                || specs.stream().noneMatch(spec -> spec.family() == MysticalAugments.Family.HEALTH_BOOST
                        && api.augments.get(spec.id()) == augment)) {
            return;
        }

        try {
            if (!Boolean.TRUE.equals(api.health.cached.invoke(cache, augment, player))) {
                return;
            }

            // Also refresh an existing native entry: swapping native -> mixed with the same
            // augment does not recreate MA's cache entry or rerun its registration branch.
            Runnable cleanup = () -> {
                var health = player.getAttribute(Attributes.MAX_HEALTH);
                if (health == null) {
                    return;
                }

                // A synced client may already lack the bonus. Subtracting it again can
                // cause a client-only death that the server cannot respawn.
                health.removeModifier(api.health.attribute);
                float maximum = player.getMaxHealth();
                if (player.getHealth() > maximum) {
                    player.setHealth(maximum);
                }
            };
            api.health.add.invoke(cache, augment, player, cleanup);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            decline(MysticalAugments.Family.HEALTH_BOOST, failure);
        }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.LEGS || !(entity instanceof Player player) || player.getItemBySlot(slot) != root) {
            return;
        }

        for (var spec : selected(root)) {
            if (spec.family().route != MysticalAugments.Route.ARMOR) {
                continue;
            }

            try {
                binding.armorTick.invoke(binding.augments.get(spec.id()), root, level, player);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error error && !(error instanceof LinkageError)) {
                    throw error;
                }
                decline(spec.family(), cause); // No rollback, fallback receiver or retry after native side effects.
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                decline(spec.family(), failure);
            }
        }
    }

    private static boolean available(MysticalAugments.Family family, Binding api) {
        return !DISABLED.contains(family) && (family != MysticalAugments.Family.HEALTH_BOOST || api.health != null)
                && (family.route == MysticalAugments.Route.ARMOR
                    ? api.tickReady.contains(family) : api.equipmentReady);
    }

    private static void decline(MysticalAugments.Family family, Throwable failure) {
        if (!DISABLED.add(family)) {
            return;
        }
        if (failure == null) {
            LeggingsContent.LOG.warn("Mystical Agriculture mixed augment disabled: {}", family);
        } else {
            LeggingsContent.LOG.warn("Mystical Agriculture mixed augment disabled: " + family, failure);
        }
    }

    private static Binding bindings() {
        if (attempted) {
            return binding;
        }
        synchronized (MysticalAugmentsCompatibility.class) {
            if (attempted) {
                return binding;
            }
            try {
                Class<?> augment = Class.forName(PREFIX + "api.tinkering.Augment");
                Class<?> tinkerable = Class.forName(PREFIX + "api.tinkering.ITinkerable");
                Class<?> entry = Class.forName(PREFIX + "api.components.AugmentComponent");
                Object registry = Class.forName(PREFIX + "api.MysticalAgricultureAPI").getMethod("getAugmentRegistry").invoke(null);
                Method lookup = registry.getClass().getMethod("getAugmentById", Identifier.class);
                var augments = new java.util.HashMap<Identifier, Object>();
                var ready = java.util.EnumSet.noneOf(MysticalAugments.Family.class);
                boolean equipment = proof(Class.forName(PREFIX + "api.util.AugmentUtils"), EQUIPMENT_PROOF);
                for (var spec : MysticalAugments.CATALOG.values()) {
                    Object value = lookup.invoke(registry, spec.id());
                    if (value == null || !value.getClass().getName().equals(spec.family().nativeClass())
                            || !spec.id().equals(augment.getMethod("getId").invoke(value))) {
                        continue;
                    }
                    augments.put(spec.id(), value);
                    if (spec.family().route == MysticalAugments.Route.ARMOR
                            && proof(value.getClass(), TICK_PROOF)) {
                        ready.add(spec.family());
                    }
                }
                DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE
                        .get(Identifier.parse("mysticalagriculture:equipped_augments")).orElseThrow().value();
                binding = new Binding(Class.forName(PREFIX + "item.armor.EssenceLeggingsItem"), entry, component,
                                      tinkerable.getMethod("getAugmentSlots"), tinkerable.getMethod("getTinkerableTier"),
                                      tinkerable.getMethod("canApplyAugment", augment), entry.getMethod("slot"), entry.getMethod("id"),
                                      augment.getMethod("onArmorTick", ItemStack.class, ServerLevel.class, Player.class),
                                      Map.copyOf(augments), Set.copyOf(ready), equipment, healthBinding());
                for (var family : MysticalAugments.Family.values()) {
                    if (!available(family, binding)) {
                        decline(family, null);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LeggingsContent.LOG.warn("Mystical Agriculture mixed augment API unavailable", failure);
            } finally {
                attempted = true;
            }
            return binding;
        }
    }

    private static boolean proof(Class<?> owner, String name) {
        try {
            Method marker = owner.getDeclaredMethod(name);
            marker.setAccessible(true);
            return Boolean.TRUE.equals(marker.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError incompatible) {
            return false;
        }
    }

    private static HealthBinding healthBinding() {
        try {
            Class<?> health = Class.forName(MysticalAugments.Family.HEALTH_BOOST.nativeClass());
            if (!proof(health, HEALTH_PROOF)) {
                return null;
            }
            var field = health.getDeclaredField("ATTRIBUTE_ID");
            field.setAccessible(true);
            if (!(field.get(null) instanceof Identifier id)) {
                return null;
            }
            Class<?> cache = Class.forName(PREFIX + "api.lib.AbilityCache");
            Class<?> augment = Class.forName(PREFIX + "api.tinkering.Augment");
            return new HealthBinding(id, cache.getMethod("isCached", augment, Player.class),
                                     cache.getMethod("add", augment, Player.class, Runnable.class));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private record Binding(Class<?> leggingsType, Class<?> entryType, DataComponentType<?> component,
                           Method slots, Method tier, Method canApply, Method slot, Method id, Method armorTick,
                           Map<Identifier, Object> augments, Set<MysticalAugments.Family> tickReady, boolean equipmentReady,
                           HealthBinding health) {
    }

    private record HealthBinding(Identifier attribute, Method cached, Method add) {
    }
}
