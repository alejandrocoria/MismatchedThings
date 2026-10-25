package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Retained independent native fixture helpers; the executable wearer prototype is retired. */
public final class SilentGearTraitSpike {
    private static NeoForgeSilentGearTest.NativeApi api;
    private static final Class<?> INSTANCE = nativeClass("api.traits.TraitInstance");
    private static final Class<?> CONTEXT = nativeClass("api.traits.TraitActionContext");
    static Thread tooltipObservation;
    static int tooltipRecalculations;

    private SilentGearTraitSpike() {
    }

    /** Share native fixture helpers without requiring execution of the unrelated wearer proof. */
    static void initialize(NeoForgeSilentGearTest.NativeApi nativeApi) {
        api = nativeApi;
    }

    public static void recalculation(ItemStack stack) {
        if (Thread.currentThread() == tooltipObservation) {
            tooltipRecalculations++;
            throw new AssertionError("Read-only production tooltip entered native recalculation");
        }
        if (SilentGearWearerFixture.active) {
            throw new AssertionError("Wearer read-only scope entered native recalculation: " + stack.getItem());
        }
    }

    /** The sources' already-computed levels are merged by ID using max, as an independent fixture oracle. */
    static Map<String, Object> view(ItemStack stack) throws Exception {
        var result = new TreeMap<String, Object>();
        var mixed = ModContent.getMixedBootsData(stack);
        List<ItemStack> sources = mixed.isPresent() ? List.of(mixed.get().left().source(), mixed.get().right().source()) : List.of(stack);
        for (ItemStack source : sources) {
            for (Object instance : (List<?>) api.helper.getMethod("getTraits", ItemInstance.class).invoke(null, source)) {
                String id = INSTANCE.getMethod("getTraitId").invoke(instance).toString();
                if (level(instance) > level(result.get(id))) {
                    result.put(id, instance);
                }
            }
        }
        return result;
    }

    static Object context(Player player, Object instance, ItemStack stack) throws Exception {
        return CONTEXT.getConstructor(Player.class, INSTANCE, ItemStack.class).newInstance(player, instance, stack);
    }

    static Object traitOf(Object instance) {
        try {
            return INSTANCE.getMethod("getTrait").invoke(instance);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    static int level(Object instance) throws Exception {
        return instance == null ? 0 : (int) INSTANCE.getMethod("getLevel").invoke(instance);
    }

    static Class<?> nativeClass(String name) {
        try {
            return Class.forName("net.silentchaos512.gear." + name);
        } catch (ClassNotFoundException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void clear(ServerPlayer player) {
        player.removeAllEffects();
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
                                          EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.tickCount = 20;
    }

    static void numericControls(ServerPlayer player, StringBuilder report) throws Exception {
        Method update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        update.setAccessible(true);
        for (String[] sample : List.of(new String[]{"azure_electrum", "movement_speed"},
                                       new String[]{"azure_silver", "gravity"}, new String[]{"feather", "safe_fall_distance"})) {
            ItemStack nativeBoot = api.boot(sample[0]);
            var attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.withDefaultNamespace(sample[1])).orElseThrow();
            var nativeEntries = nativeBoot.getAttributeModifiers().modifiers().stream()
                                          .filter(entry -> entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.FEET)).toList();
            // Upstream builds trait entries twice. Preserve this finding instead of pretending the source was normalized.
            require(nativeEntries.size() == 2 && nativeEntries.getFirst().equals(nativeEntries.getLast()),
                    "Pinned native duplicate control changed: " + sample[1] + "; entries=" + nativeEntries);
            var nativeEntry = nativeEntries.getFirst();
            require(nativeEntry.modifier().amount() != 0, "Zero native attribute control");
            var subjects = new ArrayList<ItemStack>();
            subjects.add(nativeBoot);
            subjects.addAll(NeoForgeSilentGearTest.workbench(player, nativeBoot, new ItemStack(Items.DIAMOND_BOOTS)));
            for (int index = 0; index < subjects.size(); index++) {
                ItemStack subject = subjects.get(index), before = subject.copy();
                clear(player);
                update.invoke(player);
                double base = player.getAttributeValue(attribute), expectedAmount = nativeEntry.modifier().amount() / (index == 0 ? 1 : 2);
                var entries = subject.getAttributeModifiers().modifiers().stream()
                                     .filter(entry -> entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.FEET)).toList();
                require(entries.size() == (index == 0 ? 2 : 1), "Normalized production modifier count differs");
                for (var entry : entries) {
                    require(entry.modifier().operation() == nativeEntry.modifier().operation(), "Modifier operation differs");
                    close(entry.modifier().amount(), expectedAmount, "Normalized trait amount");
                }
                double expected = switch (nativeEntry.modifier().operation()) {
                    case ADD_VALUE -> base + expectedAmount;
                    case ADD_MULTIPLIED_BASE -> base * (1 + expectedAmount);
                    default -> throw new AssertionError("Unexpected sample operation");
                };
                player.setItemSlot(EquipmentSlot.FEET, subject);
                update.invoke(player);
                close(player.getAttributeValue(attribute), expected, "Equipped trait attribute");
                require(ItemStack.matches(before, subject), "Equipment update changed source/root");
                report.append("TRAIT ATTRIBUTE ").append(sample[1]).append(" subject=").append(index)
                      .append(" operation=").append(nativeEntry.modifier().operation()).append(" amount=").append(expectedAmount)
                      .append(" rawFeetEntries=").append(entries.size()).append(" equipped=").append(expected)
                      .append(" native duplicates apply once; both production outputs compose normalized contributions\n");
            }
        }
        clear(player);
        update.invoke(player);
    }

    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
