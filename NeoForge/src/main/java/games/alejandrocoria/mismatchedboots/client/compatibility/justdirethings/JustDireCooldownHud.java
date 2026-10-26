package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Renderer-only native identity/clock view; never equipment, a source wallet or action permission. */
public final class JustDireCooldownHud {
    private static Api api;
    private static boolean attempted;
    private static boolean warned;
    private static boolean stompUnavailable;
    private static boolean earthquakeUnavailable;

    private JustDireCooldownHud() {
    }

    public static ItemStack view(ItemStack root, EquipmentSlot slot) {
        if (slot != EquipmentSlot.FEET || root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)
                || ModContent.getMixedBootsData(root).isEmpty()) {
            return root;
        }

        Api binding = binding();
        if (binding == null) {
            return root;
        }

        try {
            var records = root.getOrDefault(binding.timers, List.of());
            var counts = new HashMap<String, Integer>();
            for (Object record : records) {
                if (binding.record.isInstance(record)) {
                    counts.merge((String) binding.name.invoke(record), 1, Integer::sum);
                }
            }

            var shown = new ArrayList<Object>();
            for (Object record : records) {
                if (!binding.record.isInstance(record)) {
                    continue;
                }

                String name = (String) binding.name.invoke(record);
                if (name == null) {
                    continue;
                }
                if (!name.equals("groundstomp") && !name.equals("earthquake") || counts.get(name) != 1) {
                    continue;
                }

                int ticks = (int) binding.ticks.invoke(record);
                boolean active = (boolean) binding.active.invoke(record);
                if (ticks <= 0 || name.equals("groundstomp") && active) {
                    continue;
                }

                Profile profile = profile(binding.parameters, binding.ability, name.equals("earthquake"));
                if (profile == null) {
                    continue;
                }

                int duration = active ? profile.activeDuration : profile.cooldown;
                if (duration > 0) {
                    shown.add(binding.create.newInstance(name, Math.min(ticks, duration), active));
                }
            }
            // Native progress multiplies int counters by 8/9. Clamp only this detached view,
            // retaining record order, root evidence and the native cross-slot icon index.
            var view = binding.boots.copy();
            view.set(binding.timers, List.copyOf(shown));
            return view;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
            api = null;
            return root;
        }
    }

    private static synchronized Api binding() {
        if (attempted) {
            return api;
        }
        attempted = true;
        try {
            var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var parameters = tool.getMethod("getAbilityParams", ability);
            var boots = boots("eclipsealloy");
            if (!tool.isInstance(boots.getItem())) {
                throw new IllegalStateException("Native HUD metadata item unavailable");
            }

            DataComponentType<List<Object>> timers = timers();
            if (timers == null) {
                throw new IllegalStateException("Native cooldown component unavailable");
            }
            api = new Api(record, record.getMethod("abilityName"), record.getMethod("cooldownTicks"), record.getMethod("isactive"),
                          record.getConstructor(String.class, int.class, boolean.class), timers, boots, parameters, ability);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
        }

        return api;
    }

    /** Small HUD-only metadata binding: failure must not disable native settings or spent server clocks. */
    private static Profile profile(Method parameters, Class<?> abilityType, boolean earthquake) {
        if (earthquake ? earthquakeUnavailable : stompUnavailable) {
            return null;
        }

        try {
            Object ability = abilityType.getField(earthquake ? "EARTHQUAKE" : "GROUNDSTOMP").get(null);
            // Revalidate current parameters per view: native public metadata can change after binding.
            Object value = parameters.invoke(boots("eclipsealloy").getItem(), ability);
            Field cooldown = value.getClass().getField("cooldown");
            int duration = cooldown.getInt(value);
            int active = earthquake ? value.getClass().getField("activeCooldown").getInt(value) : 0;
            if (!safe(duration) || earthquake && !safe(active)) {
                throw new IllegalStateException("Unsafe native HUD phase duration");
            }
            // The presentation item is Eclipse, not the current settings provider. The audited
            // GS tiers share a duration; validate that assumption rather than inventing tier policy.
            if (!earthquake) {
                for (String tier : List.of("blazegold", "celestigem")) {
                    if (cooldown.getInt(parameters.invoke(boots(tier).getItem(), ability)) != duration) {
                        throw new IllegalStateException("Native GS HUD tier durations differ");
                    }
                }
            }

            return new Profile(active, duration);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (earthquake) {
                earthquakeUnavailable = true;
            } else {
                stompUnavailable = true;
            }
            warn(failure);
            return null;
        }
    }

    private static boolean safe(int duration) {
        return duration > 0 && duration <= Integer.MAX_VALUE / 9;
    }

    private static ItemStack boots(String tier) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots")).orElseThrow().value());
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<List<Object>> timers() {
        return (DataComponentType<List<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", "ability_cooldowns")).map(holder -> holder.value()).orElse(null);
    }

    private static void warn(Throwable failure) {
        if (!warned) {
            warned = true;
            Constants.LOG.warn("Just Dire Things cooldown HUD metadata unavailable; dependent mixed icons disabled", failure);
        }
    }

    private record Profile(int activeDuration, int cooldown) {
    }

    private record Api(Class<?> record, Method name, Method ticks, Method active, Constructor<?> create,
                       DataComponentType<List<Object>> timers, ItemStack boots, Method parameters, Class<?> ability) {
    }
}
