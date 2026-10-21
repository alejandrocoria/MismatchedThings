package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Actual integrated connection/menu packets; deterministic window polling is restricted to capture scopes. */
public final class SilentGearTooltipClientSpike {
    private static Set<Integer> pressed;
    private static boolean capturing, finished, presentation = true, refused;
    private static int ticks, events, captures, comparisons, maxLines, maxNativeLines, maxWidth;
    private static Future<String> seed;
    private static JsonArray manifest;
    private static KeyMapping properties, traits, construction;
    private static Object vanilla;
    private static final String OUTPUT = "silent-gear-tooltip-spike";

    private SilentGearTooltipClientSpike() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ItemTooltipEvent event) -> {
            if (capturing) { events++; }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    public static Boolean key(int code) { return pressed == null ? null : pressed.contains(code); }
    public static boolean omitPresentation() { return !presentation || refused; }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 3_600, "SG tooltip spike timed out");
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            if (seed == null) {
                require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Tooltip fixture window is not hidden");
                client.options.pauseOnLostFocus = false;
                var server = client.getSingleplayerServer(); var id = client.player.getUUID();
                seed = server.submit(() -> {
                    try {
                        var owner = server.getPlayerList().getPlayer(id);
                        var api = new NeoForgeSilentGearTest.NativeApi();
                        ItemStack iron = api.boot("iron");
                        iron.set(DataComponents.CUSTOM_NAME, Component.literal("Named SG source"));
                        iron.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Keep original lore"))));
                        Object swap = api.swapClass.getField("INSTANCE").get(null);
                        ItemStack lined = swap(api, swap, iron, api.part("lining", "wool"));
                        ItemStack coated = swap(api, swap, api.boot("copper"), api.part("coating", "gold"));
                        ItemStack upgrade = swap(api, swap, api.boot("iron"), nativeItem("silentgear:magnetic_upgrade"));
                        ItemStack copper = api.boot("copper");
                        copper.set(NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER"), 1199);
                        var samples = new ArrayList<ItemStack>();
                        samples.addAll(NeoForgeSilentGearTest.workbench(owner, iron, new ItemStack(Items.DIAMOND_BOOTS)));
                        samples.addAll(NeoForgeSilentGearTest.workbench(owner, lined, api.boot("gold")));
                        samples.addAll(NeoForgeSilentGearTest.workbench(owner, coated, upgrade));
                        var used = NeoForgeSilentGearTest.workbench(owner, copper, new ItemStack(Items.DIAMOND_BOOTS));
                        for (ItemStack root : used) {
                            root.setDamageValue(17);
                            SilentGearLifecycleTest.tick(owner, root, 20, null);
                            SilentGearLifecycleTest.tick(owner, root, 21, net.minecraft.world.entity.EquipmentSlot.FEET);
                            var state = root.get(SilentGearCompatibility.state());
                            require(state.left().or(() -> state.right()).orElseThrow().phase().equals("oxidized"),
                                    "Used tooltip control did not advance live oxidation");
                        }
                        samples.addAll(used);
                        samples.addAll(NeoForgeSilentGearTest.workbench(owner, iron,
                                nativeItem("justdirethings:eclipsealloy_boots")));
                        // Chest storage does not tick its contents. Real menu packets carry every full root/source/state.
                        var container = new SimpleContainer(27);
                        var expected = new JsonArray();
                        var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                        for (int index = 0; index < samples.size(); index++) {
                            container.setItem(index, samples.get(index));
                            expected.add(ItemStack.CODEC.encodeStart(ops, samples.get(index)).getOrThrow());
                        }
                        owner.openMenu(new SimpleMenuProvider((menuId, inventory, player) ->
                                ChestMenu.threeRows(menuId, inventory, container), Component.literal("SG tooltip spike")));
                        return expected.toString();
                    } catch (Exception failure) { throw new IllegalStateException(failure); }
                });
                return;
            }
            if (!seed.isDone()) { return; }
            if (manifest == null) { manifest = JsonParser.parseString(seed.get()).getAsJsonArray(); }
            if (!(client.player.containerMenu instanceof ChestMenu)) { return; }
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (int index = 0; index < manifest.size(); index++) {
                ItemStack expected = ItemStack.CODEC.parse(ops, manifest.get(index)).getOrThrow();
                if (!ItemStack.matches(expected, client.player.containerMenu.getSlot(index).getItem())) { return; }
            }
            run(client);
            finished = true; client.stop();
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(OUTPUT, failure); finished = true; client.stop();
        }
    }

    private static void run(Minecraft client) throws Exception {
        Class<?> keys = Class.forName("net.silentchaos512.gear.client.KeyTracker");
        properties = (KeyMapping) keys.getField("DISPLAY_PROPERTIES").get(null);
        traits = (KeyMapping) keys.getField("DISPLAY_TRAITS").get(null);
        construction = (KeyMapping) keys.getField("DISPLAY_CONSTRUCTION").get(null);
        vanilla = Class.forName("net.silentchaos512.gear.Config$Client").getField("vanillaStyleTooltips").get(null);
        var oldKeys = List.of(properties.getKey(), traits.getKey(), construction.getKey());
        boolean oldVanilla = (boolean) vanilla.getClass().getMethod("get").invoke(vanilla);
        var report = new StringBuilder("Production source details; independent native event control; no extra source heading/root SG table\n");
        try {
            for (boolean simple : List.of(false, true)) {
                vanilla.getClass().getMethod("set", Object.class).invoke(vanilla, simple);
                for (int mode = 0; mode < 8; mode++) {
                    boolean ctrl = (mode & 1) != 0, shift = (mode & 2) != 0, alt = (mode & 4) != 0;
                    input(ctrl, shift, alt, false);
                    require((boolean) keys.getMethod("isDisplayPropertiesDown").invoke(null) == ctrl
                            && (boolean) keys.getMethod("isDisplayConstructionDown").invoke(null) == alt
                            && (boolean) keys.getMethod("isDisplayTraitDescriptionsDown").invoke(null) == shift,
                            "Native key tracker did not read controlled window input");
                    for (int index = 0; index < manifest.size(); index++) {
                        compare(client, client.player.containerMenu.getSlot(index).getItem(), mode == 7 ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL, true);
                    }
                    report.append("native/mixed equality: vanillaStyle=").append(simple).append(" keys=").append(mode)
                            .append(" ten paid connected roots\n");
                }
            }
            vanilla.getClass().getMethod("set", Object.class).invoke(vanilla, false);
            properties.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P));
            traits.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T));
            construction.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C));
            input(true, true, true, true);
            require((boolean) keys.getMethod("isDisplayPropertiesDown").invoke(null)
                    && (boolean) keys.getMethod("isDisplayConstructionDown").invoke(null)
                    && (boolean) keys.getMethod("isDisplayTraitDescriptionsDown").invoke(null), "Rebound native keys failed");
            compare(client, client.player.containerMenu.getSlot(4).getItem(), TooltipFlag.ADVANCED, true);
            input(false, false, false, true);
            compare(client, client.player.containerMenu.getSlot(4).getItem(), TooltipFlag.NORMAL, true);
            report.append("rebound P/T/C: native queries, hints and structured components agree\n");
            properties.setKey(oldKeys.get(0)); traits.setKey(oldKeys.get(1)); construction.setKey(oldKeys.get(2));
            input(false, false, false, false);
            ItemStack normal = client.player.containerMenu.getSlot(2).getItem();
            var data = ModContent.getMixedBootsData(normal).orElseThrow();
            var props = NeoForgeSilentGearTest.NativeApi.component("GEAR_PROPERTIES");
            var parts = NeoForgeSilentGearTest.NativeApi.component("GEAR_CONSTRUCTION");
            var visible = SilentGearNativeTooltipControl.details(client, data.left().source(), TooltipFlag.NORMAL);
            require(visible.size() >= 2, "Visibility control lacks native hints/traits");
            for (int hidden = 1; hidden <= 4; hidden++) {
                ItemStack source = data.left().source();
                TooltipDisplay display = TooltipDisplay.DEFAULT;
                if ((hidden & 1) != 0) { display = display.withHidden(props, true); }
                if ((hidden & 2) != 0) { display = display.withHidden(parts, true); }
                if (hidden == 4) { display = new TooltipDisplay(true, display.hiddenComponents()); }
                source.set(DataComponents.TOOLTIP_DISPLAY, display);
                var selected = SilentGearNativeTooltipControl.details(client, source, TooltipFlag.NORMAL);
                List<Component> expected = hidden == 1 ? visible.subList(visible.size() - 1, visible.size())
                        : hidden == 2 ? visible.subList(0, visible.size() - 1) : List.of();
                require(selected.equals(expected), "Hidden native group leaked or left a dangling hint: " + hidden);
                ItemStack probe = replace(normal, source);
                compare(client, probe, TooltipFlag.NORMAL, false);
                input(true, true, true, false);
                var expanded = SilentGearNativeTooltipControl.details(client, source, TooltipFlag.ADVANCED);
                require(expanded.stream().anyMatch(line -> translation(line, "misc.silentgear.tooltip.properties")) == (hidden == 2)
                        && expanded.stream().anyMatch(line -> translation(line, "misc.silentgear.tooltip.construction")) == (hidden == 1),
                        "Expanded hidden native group visibility differs: " + hidden);
                compare(client, probe, TooltipFlag.ADVANCED, false);
                input(false, false, false, false);
            }
            ItemStack absent = data.left().source(); absent.remove(props);
            require(SilentGearNativeTooltipControl.details(client, absent, TooltipFlag.NORMAL).isEmpty(), "Missing native cache reached formatters");
            compare(client, replace(normal, absent), TooltipFlag.NORMAL, false);
            ItemStack broken = data.left().source(); broken.setDamageValue(broken.getMaxDamage());
            require(capture(client, broken, TooltipFlag.NORMAL, false).stream().anyMatch(line -> translation(line, "misc.silentgear.broken")),
                    "Native Broken diagnostic control missing");
            compare(client, replace(normal, broken), TooltipFlag.NORMAL, false);
            require(SilentGearNativeTooltipControl.details(client, broken, TooltipFlag.NORMAL).stream().noneMatch(line -> translation(line, "misc.silentgear.broken")),
                    "Control imported native Broken diagnostic");
            refused = true;
            compare(client, normal, TooltipFlag.NORMAL, false);
            refused = false;
            ItemStack hiddenRoot = normal.copy();
            hiddenRoot.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
            require(capture(client, hiddenRoot, TooltipFlag.NORMAL, false).equals(capture(client, hiddenRoot, TooltipFlag.NORMAL, true)),
                    "Hidden root leaked production details");
            report.append("compact/expanded visibility, missing-cache, Broken/refused: no leaked groups or native recalculation; unrelated details retained\n");
            report.append("full captures=").append(captures).append(" structured mixed comparisons=").append(comparisons)
                    .append(" recalculations=").append(SilentGearTraitSpike.tooltipRecalculations).append('\n');
            report.append("layout measurement: maxLines=").append(maxLines).append(" nativeMaxLines=").append(maxNativeLines).append(" maxWidth=").append(maxWidth)
                    .append(" scaledWindow=").append(client.getWindow().getGuiScaledWidth()).append('x')
                    .append(client.getWindow().getGuiScaledHeight()).append("; no manual visual acceptance\n");
            NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", report.toString());
            NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "neoforge\n");
        } finally {
            properties.setKey(oldKeys.get(0)); traits.setKey(oldKeys.get(1)); construction.setKey(oldKeys.get(2));
            traits.setDown(false); pressed = null; presentation = true;
            refused = false; SilentGearTraitSpike.tooltipObservation = null;
            vanilla.getClass().getMethod("set", Object.class).invoke(vanilla, oldVanilla);
        }
    }

    private static void compare(Minecraft client, ItemStack root, TooltipFlag flag, boolean nativeControl) throws Exception {
        ItemStack before = root.copy();
        SilentGearTraitSpike.tooltipObservation = Thread.currentThread();
        try {
            var baseline = capture(client, root, flag, false);
            var expected = new ArrayList<>(baseline);
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            var sources = List.of(data.left().source(), data.right().source());
            for (int side = 0; side < 2; side++) {
                ItemStack source = sources.get(side);
                List<Component> block = refused ? List.of() : SilentGearNativeTooltipControl.details(client, source, flag);
                if (!block.isEmpty() && nativeControl) {
                    var nativeLines = capture(client, source, flag, false);
                    require(nativeLines.size() > block.size() && nativeLines.subList(1, 1 + block.size()).equals(block),
                            "Native control differs from complete native event output: " + source.getHoverName().getString());
                }
                int header = side(expected, side == 0 ? "left" : "right");
                require(header >= 0, "Existing side identity disappeared");
                expected.addAll(header + 1, block);
            }
            var actual = capture(client, root, flag, true);
            require(expected.equals(actual), "Mixed order/style/root text differs from isolated native source insertion");
            if (sources.stream().anyMatch(source -> BuiltInRegistries.ITEM.getKey(source.getItem()).getNamespace().equals("justdirethings"))) {
                require(actual.stream().filter(line -> translation(line, "justdirethings.festored")).count() == 1
                        && actual.stream().filter(line -> translation(line, "justdirethings.enabled") || translation(line, "justdirethings.disabled")).count() == 1,
                        "JDT coexistence lacks its single actual root energy/global-toggle block");
            }
            require(ItemStack.matches(before, root), "Tooltip mutated root/source/live state");
            if (comparisons == 0) {
                NetworkAcceptanceFiles.write(OUTPUT + "-compact.txt", actual.stream().map(Component::getString).collect(java.util.stream.Collectors.joining("\n")));
            }
            maxLines = Math.max(maxLines, actual.size());
            for (Component line : actual) { maxWidth = Math.max(maxWidth, client.font.width(line)); }
            comparisons++;
        } finally { SilentGearTraitSpike.tooltipObservation = null; }
    }

    private static List<Component> capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean production) {
        presentation = production; events = 0; capturing = true;
        try {
            var lines = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
            require(events <= 1 && (stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip() || events == 1),
                    "Missing or recursively replayed final tooltip event: " + events);
            if (!ModContent.isMixedBoots(stack)) { maxNativeLines = Math.max(maxNativeLines, lines.size()); }
            captures++; return lines;
        } finally { capturing = false; }
    }

    private static void input(boolean ctrl, boolean shift, boolean alt, boolean rebound) {
        pressed = new HashSet<>();
        if (ctrl) { pressed.add(rebound ? GLFW.GLFW_KEY_P : GLFW.GLFW_KEY_LEFT_CONTROL); }
        if (shift) { pressed.add(rebound ? GLFW.GLFW_KEY_T : GLFW.GLFW_KEY_LEFT_SHIFT); }
        if (alt) { pressed.add(rebound ? GLFW.GLFW_KEY_C : GLFW.GLFW_KEY_LEFT_ALT); }
        traits.setDown(shift);
    }

    private static ItemStack replace(ItemStack root, ItemStack source) {
        ItemStack copy = root.copy(); var data = ModContent.getMixedBootsData(root).orElseThrow();
        copy.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(source), data.right()));
        return copy;
    }

    private static int side(List<Component> lines, String side) {
        for (int index = 0; index < lines.size(); index++) {
            if (translation(lines.get(index), "tooltip.mismatchedboots.mixed_boots." + side)) { return index; }
        }
        return -1;
    }

    private static boolean translation(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)) { return true; }
        return line.getSiblings().stream().anyMatch(child -> translation(child, key));
    }

    private static ItemStack swap(NeoForgeSilentGearTest.NativeApi api, Object recipe, ItemStack base, ItemStack addition) throws Exception {
        return (ItemStack) api.assemble.invoke(recipe, net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(base.copy(), addition)));
    }

    private static ItemStack nativeItem(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow().value());
    }
}
