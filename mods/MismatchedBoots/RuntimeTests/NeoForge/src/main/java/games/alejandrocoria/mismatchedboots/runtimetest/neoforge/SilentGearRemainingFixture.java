package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Independent production witnesses and explicitly synthetic cure acquisition. No gameplay adapter. */
public final class SilentGearRemainingFixture {
    public static int queries;
    public static int pushes;
    public static int cureTicks;
    public static final Map<String, Integer> cureCalls = new HashMap<>(); // Integrated-server thread only.

    private SilentGearRemainingFixture() {
    }

    /** Three used paid roots; normal connected controls already cover both orientations of every feature. */
    static List<ItemStack> persisted(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        var roots = List.of(
                NeoForgeSilentGearTest.workbench(player, api.boot("tyrian_steel"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst(),
                NeoForgeSilentGearTest.workbench(player, api.boot("leather"), new ItemStack(Items.DIAMOND_BOOTS)).get(1),
                NeoForgeSilentGearTest.workbench(player, injected(api, "cure_poison"), injected(api, "cure_wither")).getFirst());
        for (var root : roots) {
            root.setDamageValue(3);
        }
        selected(player, roots);
        manualCommands(player, api);
        return roots;
    }

    static void selected(ServerPlayer player, List<ItemStack> roots) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }

        double y = player.getY();
        try {
            player.removeAllEffects();
            var before = roots.stream().map(ItemStack::copy).toList();
            player.setItemSlot(EquipmentSlot.FEET, roots.getFirst());
            player.setPos(player.getX(), player.level().getMinY() - 32.5, player.getZ());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            SilentGearLifecycleTest.require(player.hasEffect(MobEffects.LEVITATION) && player.hasEffect(MobEffects.SLOW_FALLING),
                                            "Persisted Void Ward did not rescue");
            SilentGearLifecycleTest.require(roots.get(1).canWalkOnPowderedSnow(player), "Persisted Snow Walker absent");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200));
            roots.get(2).inventoryTick(player.level(), player, EquipmentSlot.OFFHAND);
            SilentGearLifecycleTest.require(!player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER)
                                                    && player.hasEffect(MobEffects.NIGHT_VISION),
                                            "Persisted distinct cures did not cancel selectively");
            for (int i = 0; i < roots.size(); i++) {
                SilentGearLifecycleTest.require(ItemStack.matches(before.get(i), roots.get(i)), "Remaining effect changed persisted root/history");
            }
        } finally {
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.removeAllEffects();
            player.setPos(player.getX(), y, player.getZ());
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    /** Full native source commands, verified by the real vanilla parser; command block if longer than chat. */
    private static void manualCommands(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        var text = new StringBuilder("TEST SOURCES ONLY: computed traits injected into ordinary native iron boots; no stock recipe.\n"
                                             + "In a test world with cheats, paste each /give command into a command block near you and activate it.\n"
                                             + "Commands use @p because a command block has no player @s; their item arguments were verified with the vanilla parser.\n"
                                             + "Combine the given native boots in the Workbench with diamond boots (or one of each single cure).\n"
                                             + "Do not recalculate/rebuild their native materials before combining: that legitimately replaces synthetic traits.\n\n");
        var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var names : List.of(List.of("cure_poison"), List.of("cure_wither"), List.of("cure_poison", "cure_wither"))) {
            var source = injected(api, names.toArray(String[]::new));
            var encoded = ItemStack.CODEC.encodeStart(ops, source).getOrThrow().getAsJsonObject();
            var components = new java.util.ArrayList<String>();
            for (var entry : encoded.getAsJsonObject("components").entrySet()) {
                components.add(entry.getKey().startsWith("!") ? entry.getKey() : entry.getKey() + "=" + entry.getValue());
            }

            String item = encoded.get("id").getAsString() + "[" + String.join(",", components) + "]";
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.level().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "give @s " + item);
            var given = player.getMainHandItem();
            SilentGearLifecycleTest.require(ItemStack.matches(source, given), "Manual cure source command changed native components");
            for (String name : names) {
                SilentGearLifecycleTest.require(api.traitLevel(given, name) == 1, "Manual cure source lacks " + name);
            }

            var root = NeoForgeSilentGearTest.workbench(player, given, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
            root.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
            SilentGearLifecycleTest.require(player.hasEffect(MobEffects.POISON) != names.contains("cure_poison")
                                                    && player.hasEffect(MobEffects.WITHER) != names.contains("cure_wither"),
                                            "Manual cure source failed production dispatch");
            player.removeAllEffects();
            text.append(String.join(" + ", names)).append('\n').append("/give @p ").append(item).append("\n\n");
        }
        player.getInventory().clearContent();
        NetworkAcceptanceFiles.write("silent-gear-remaining-manual.txt", text.toString());
    }

    /** Explicit synthetic computed-trait acquisition, not a native material/part recipe. */
    @SuppressWarnings("unchecked")
    static ItemStack injected(NeoForgeSilentGearTest.NativeApi api, String... names) throws Exception {
        ItemStack source = api.boot("iron");
        Object props = source.get(api.properties);
        var values = new HashMap<>((Map<Object, Object>) props.getClass().getMethod("properties").invoke(props));
        var instances = new java.util.ArrayList<Object>();
        Class<?> trait = SilentGearTraitSpike.nativeClass("gear.trait.Trait");
        Class<?> instance = SilentGearTraitSpike.nativeClass("api.traits.TraitInstance");
        for (String name : names) {
            Object ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:" + name);
            Object definition = ((Supplier<?>) ref).get();
            instances.add(instance.getMethod("of", trait, int.class, java.util.Collection.class).invoke(null, definition, 1, List.of()));
        }

        Object key = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        values.put(key, SilentGearTraitSpike.nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(instances));
        source.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(values));
        for (String name : names) {
            if (api.traitLevel(source, name) != 1) {
                throw new AssertionError("Invalid injected native trait: " + name);
            }
        }

        return source;
    }
}
