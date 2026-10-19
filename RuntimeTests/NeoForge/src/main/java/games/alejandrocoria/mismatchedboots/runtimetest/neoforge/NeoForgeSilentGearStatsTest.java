package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Optional diagnostic, not a production adapter or an exhaustive Silent Gear trait suite. */
public final class NeoForgeSilentGearStatsTest {
    private static boolean finished;

    private NeoForgeSilentGearStatsTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            var api = new NativeApi();
            var player = FakePlayerFactory.getMinecraft(server.overworld());
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(0, 80, 0); player.setNoGravity(true);
            var report = new StringBuilder("Native construction API; default composition policies; FEET additive modifiers\n");
            var profiles = new TreeMap<String, ItemStack>();
            for (String material : List.of("iron", "diamond", "leather", "gold")) {
                profiles.put(material, api.boot(material));
            }
            ItemStack iron = profiles.get("iron");
            Object swap = api.swapClass.getField("INSTANCE").get(null);
            var liningInput = CraftingInput.of(2, 1, List.of(iron.copy(), api.part("lining", "wool")));
            require((boolean) api.matches.invoke(swap, liningInput, server.overworld()), "Native wool lining recipe rejected iron boots");
            ItemStack lined = (ItemStack) api.assemble.invoke(swap, liningInput);
            require(!lined.isEmpty() && !ItemStack.matches(iron, lined), "Native lining recipe did not change boots");
            profiles.put("iron+wool", lined);
            require(contributions(lined).getOrDefault("minecraft:knockback_resistance", 0D)
                    > contributions(iron).getOrDefault("minecraft:knockback_resistance", 0D), "Lining control did not change its numeric stat");
            for (var profile : profiles.entrySet()) {
                ItemStack stack = profile.getValue();
                require(stack.getMaxDamage() > 0 && stack.has(DataComponents.MAX_DAMAGE), "Native durability was not materialized");
                report.append("\nNATIVE ").append(profile.getKey()).append(" max=").append(stack.getMaxDamage())
                        .append(" attrs=").append(contributions(stack)).append('\n');
                api.describe(stack, report);
                equipAndCheck(player, stack, report);
                stack.setDamageValue(17);
                ItemStack vanilla = new ItemStack(Items.DIAMOND_BOOTS); vanilla.setDamageValue(32);
                measure(player, profile.getKey() + " / vanilla diamond", stack, vanilla, api, report);
            }
            measure(player, "iron+wool / SG diamond", lined, profiles.get("diamond"), api, report);
            measure(player, "iron+wool / identical", lined, lined, api, report);
            // Trait queries intentionally do not call activateTraits(nonGear), which logs an upstream error.
            for (String[] sample : List.of(new String[]{"leather", "snow_walker"}, new String[]{"gold", "brilliant"})) {
                ItemStack nativeBoot = profiles.get(sample[0]);
                int nativeLevel = api.traitLevel(nativeBoot, sample[1]);
                require(nativeLevel > 0, "Native trait control is missing: " + sample[1]);
                for (ItemStack root : workbench(player, nativeBoot, new ItemStack(Items.DIAMOND_BOOTS))) {
                    require(api.traitLevel(root, sample[1]) == 0, "Identity-gated trait unexpectedly recognizes root");
                    boolean snow = root.getItem().canWalkOnPowderedSnow(root, player);
                    boolean piglin = root.getItem().makesPiglinsNeutral(root, player);
                    report.append("TRAIT ").append(sample[1]).append(" nativeLevel=").append(nativeLevel)
                            .append(" rootLevel=0 nativeSnow=").append(nativeBoot.getItem().canWalkOnPowderedSnow(nativeBoot, player))
                            .append(" rootSnow=").append(snow).append(" nativePiglin=")
                            .append(nativeBoot.getItem().makesPiglinsNeutral(nativeBoot, player)).append(" rootPiglin=").append(piglin).append('\n');
                    var input = CraftingInput.of(2, 1, List.of(root.copy(), api.part("lining", "wool")));
                    require(!(boolean) api.matches.invoke(swap, input, server.overworld()), "Native part swap unexpectedly accepts mixed root");
                }
            }
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            SilentGearTraitSpike.run(player, api, report);
            report.append("\nBaseline: 14 numeric paid output measurements; additional paid trait-spike pairs above; source snapshots unchanged; ")
                    .append("trait/item callback probes are not full gameplay acceptance\n");
            NetworkAcceptanceFiles.write("silent-gear-stats-report.txt", report.toString());
            NetworkAcceptanceFiles.write("silent-gear-stats-success.txt", "neoforge\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("silent-gear-stats", failure); }
        finally { server.halt(false); }
    }

    private static void measure(ServerPlayer player, String label, ItemStack first, ItemStack second,
                                NativeApi api, StringBuilder report) throws Exception {
        var outputs = workbench(player, first, second);
        var a = contributions(first); var b = contributions(second);
        var ids = new TreeSet<>(a.keySet()); ids.addAll(b.keySet());
        int max = (first.getMaxDamage() + second.getMaxDamage()) / 2;
        int remaining = ((first.getMaxDamage() - first.getDamageValue()) + (second.getMaxDamage() - second.getDamageValue())) / 2;
        for (int index = 0; index < outputs.size(); index++) {
            ItemStack root = outputs.get(index), owner = index == 0 ? first : second;
            require(root.getMaxDamage() == max && root.getDamageValue() == max - remaining, "Durability differs: " + label);
            var expected = new TreeMap<String, Double>();
            for (String id : ids) {
                var policy = AttributeRulesLifecycle.serverRules().policyFor(Identifier.parse(id));
                require(policy == AttributePolicy.AVERAGE || policy == AttributePolicy.SEPARATE, "Unexpected fixture policy: " + id);
                double value = policy == AttributePolicy.AVERAGE ? (a.getOrDefault(id, 0D) + b.getOrDefault(id, 0D)) / 2
                        : (index == 0 ? a : b).getOrDefault(id, 0D);
                if (value != 0) { expected.put(id, value); }
            }
            var actual = contributions(root);
            require(expected.keySet().equals(actual.keySet()), "Attribute set differs: " + label + ": " + expected + " vs " + actual);
            for (String id : expected.keySet()) { close(actual.get(id), expected.get(id), label + " " + id); }
            for (var type : List.of(api.construction, api.properties)) {
                require(java.util.Objects.equals(root.get(type), owner.get(type)), "Unknown native component is not owner-owned");
            }
            equipAndCheck(player, root, report);
            report.append("MIXED ").append(label).append(index == 0 ? " primary" : " inverse")
                    .append(" max=").append(max).append(" damage=").append(root.getDamageValue())
                    .append(" attrs=").append(actual).append(" ownerNativeComponents=true\n");
        }
    }

    /** Independent sum oracle is deliberately bounded to these samples' FEET ADD_VALUE modifiers. */
    private static Map<String, Double> contributions(ItemStack stack) {
        var result = new TreeMap<String, Double>();
        for (var entry : stack.getAttributeModifiers().modifiers()) {
            require(entry.slot().test(EquipmentSlot.FEET) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE,
                    "Sample needs a broader numeric oracle: " + entry);
            String id = entry.attribute().unwrapKey().orElseThrow().identifier().toString();
            result.merge(id, entry.modifier().amount(), Double::sum);
        }
        result.values().removeIf(value -> value == 0);
        return result;
    }

    private static void equipAndCheck(ServerPlayer player, ItemStack stack, StringBuilder report) throws Exception {
        // Invoke the vanilla equipment-update boundary, not manual modifier installation.
        Method update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); update.setAccessible(true);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); update.invoke(player);
        var baseline = new TreeMap<String, Double>();
        for (String id : contributions(stack).keySet()) {
            var attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id)).orElseThrow();
            if (player.getAttribute(attribute) == null) {
                require(id.equals("silentgear:magic_armor"), "Unexpected missing player attribute: " + id);
                report.append("EQUIPMENT unavailable player attribute=").append(id).append(" (not fabricated)\n");
                continue;
            }
            baseline.put(id, player.getAttributeValue(attribute));
        }
        player.setItemSlot(EquipmentSlot.FEET, stack.copy()); update.invoke(player);
        for (var value : contributions(stack).entrySet()) {
            if (!baseline.containsKey(value.getKey())) { continue; }
            var attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(value.getKey())).orElseThrow();
            close(player.getAttributeValue(attribute) - baseline.get(value.getKey()), value.getValue(), "Equipped " + value.getKey());
        }
    }

    static List<ItemStack> workbench(ServerPlayer player, ItemStack first, ItemStack second) {
        var menu = new MismatchedWorkbenchMenu(1, player.getInventory());
        menu.getSlot(0).set(first.copy()); menu.getSlot(1).set(second.copy());
        var previews = List.of(menu.getSlot(2).getItem().copy(), menu.getSlot(3).getItem().copy());
        require(previews.stream().noneMatch(ItemStack::isEmpty) && !menu.isCommitted(), "Missing previews");
        ItemStack primary = menu.getSlot(2).safeTake(1, 1, player);
        require(menu.isCommitted() && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "Workbench did not debit inputs");
        var result = List.of(primary, menu.getSlot(3).safeTake(1, 1, player));
        for (int index = 0; index < 2; index++) {
            require(ItemStack.matches(previews.get(index), result.get(index)), "Preview differs from paid output");
            var data = ModContent.getMixedBootsData(result.get(index)).orElseThrow();
            require(ItemStack.matches(data.left().source(), index == 0 ? first : second)
                    && ItemStack.matches(data.right().source(), index == 0 ? second : first), "Source data changed");
        }
        return result;
    }

    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    /** Reflection keeps optional native classes out of the ordinary fixture compile classpath. */
    static final class NativeApi {
        final Class<?> resource = Class.forName("net.silentchaos512.gear.api.util.DataResource");
        final Class<?> material = Class.forName("net.silentchaos512.gear.gear.material.MaterialInstance");
        final Class<?> part = Class.forName("net.silentchaos512.gear.gear.part.PartInstance");
        final Class<?> gear = Class.forName("net.silentchaos512.gear.api.item.GearItem");
        final Class<?> helper = Class.forName("net.silentchaos512.gear.util.TraitHelper");
        final Class<?> swapClass = Class.forName("net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe");
        final Method matches = swapClass.getMethod("matches", CraftingInput.class, Level.class);
        final Method assemble = swapClass.getMethod("assemble", CraftingInput.class);
        final DataComponentType<Object> construction = component("GEAR_CONSTRUCTION");
        final DataComponentType<Object> properties = component("GEAR_PROPERTIES");

        NativeApi() throws ReflectiveOperationException {}

        ItemStack part(String name, String mat) throws Exception {
            Object ref = resource.getMethod("material", String.class).invoke(null, "silentgear:" + mat);
            Object instance = material.getMethod("of", resource).invoke(null, ref);
            var item = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("silentgear", name)).orElseThrow().value();
            return (ItemStack) item.getClass().getMethod("create", List.class).invoke(item, List.of(instance));
        }

        ItemStack boot(String mat) throws Exception {
            return armor("boots", "boot_plates", mat);
        }

        ItemStack armor(String name, String platesName, String mat) throws Exception {
            var item = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("silentgear", name)).orElseThrow().value();
            Object plates = part.getMethod("from", ItemStack.class).invoke(null, part(platesName, mat));
            return (ItemStack) gear.getMethod("construct", Collection.class).invoke(item, List.of(plates));
        }

        int traitLevel(ItemStack stack, String name) throws Exception {
            Object ref = resource.getMethod("trait", String.class).invoke(null, "silentgear:" + name);
            return (int) helper.getMethod("getTraitLevel", ItemInstance.class, resource).invoke(null, stack, ref);
        }

        @SuppressWarnings("unchecked")
        private static DataComponentType<Object> component(String field) throws ReflectiveOperationException {
            Object supplier = Class.forName("net.silentchaos512.gear.setup.SgDataComponents").getField(field).get(null);
            return (DataComponentType<Object>) ((Supplier<?>) supplier).get();
        }

        @SuppressWarnings("unchecked")
        void describe(ItemStack stack, StringBuilder report) throws Exception {
            Object props = stack.get(properties);
            require(props != null && stack.get(construction) != null, "Native construction missing");
            var registry = (Registry<Object>) Class.forName("net.silentchaos512.gear.setup.SgRegistries").getField("GEAR_PROPERTY").get(null);
            var values = (Map<?, ?>) props.getClass().getMethod("properties").invoke(props);
            var sorted = new TreeMap<String, Object>();
            Method payload = Class.forName("net.silentchaos512.gear.api.property.GearPropertyValue").getMethod("value");
            for (var entry : values.entrySet()) { sorted.put(registry.getKey(entry.getKey()).toString(), payload.invoke(entry.getValue())); }
            report.append("PROPERTIES ").append(sorted).append('\n');
            report.append("MODIFIERS ").append(stack.getAttributeModifiers().modifiers()).append('\n');
        }
    }
}
