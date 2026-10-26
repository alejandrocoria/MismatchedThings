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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Actual integrated connection/menu packets; deterministic window polling is restricted to capture scopes. */
public final class SilentGearTooltipClientTest {
    private static Set<Integer> pressed;
    private static boolean capturing;
    private static boolean finished;
    private static boolean presentation = true;
    private static boolean expanded;
    private static boolean faultActive;
    private static int faultCalls;
    private static int ticks;
    private static int events;
    private static int captures;
    private static int comparisons;
    private static int maxLines;
    private static int maxNativeLines;
    private static int maxWidth;
    private static Future<String> seed;
    private static JsonArray manifest;
    private static KeyMapping properties;
    private static KeyMapping traits;
    private static KeyMapping construction;
    private static Object vanilla;
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.silentGearTooltipVariant", "normal");
    private static final String OUTPUT = "silent-gear-tooltip-" + VARIANT;

    private SilentGearTooltipClientTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ItemTooltipEvent event) -> {
            if (capturing) {
                events++;
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    public static Boolean key(int code) {
        return pressed == null ? null : pressed.contains(code);
    }

    public static boolean omitPresentation() {
        return !presentation;
    }

    public static void bindingFault() throws ClassNotFoundException {
        if (faultActive && VARIANT.equals("binding")) {
            faultCalls++;
            throw new ClassNotFoundException("Controlled SG tooltip binding refusal");
        }
    }

    public static void formatterFault(List<Component> scratch) {
        if (faultActive && VARIANT.equals("formatter")) {
            require(scratch.size() > 1, "Formatter fault ran before native scratch-list population");
            scratch.add(Component.literal("MUST NOT PUBLISH PARTIAL SG BLOCK"));
            faultCalls++;
            // An unexpected helper argument failure is not proof of malformed source data.
            throw new IllegalArgumentException("Controlled SG tooltip formatter failure after population");
        }
    }

    private static void tick(Minecraft client) {
        if (finished) {
            return;
        }

        try {
            require(++ticks < 3_600, "SG tooltip acceptance timed out");
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            if (seed == null) {
                require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Tooltip fixture window is not hidden");
                client.options.pauseOnLostFocus = false;
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
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
                        ItemStack jdt = nativeItem("justdirethings:eclipsealloy_boots");
                        @SuppressWarnings("unchecked") var energy = (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE
                                .get(Identifier.parse("justdirethings:forge_energy")).orElseThrow().value();
                        jdt.set(energy, 177_360);
                        samples.addAll(NeoForgeSilentGearTest.workbench(owner, iron, jdt));
                        // Chest storage does not tick its contents. Real menu packets carry every full root/source/state.
                        var container = new SimpleContainer(27);
                        var expected = new JsonArray();
                        var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                        for (int index = 0; index < samples.size(); index++) {
                            container.setItem(index, samples.get(index));
                            expected.add(ItemStack.CODEC.encodeStart(ops, samples.get(index)).getOrThrow());
                        }
                        owner.openMenu(new SimpleMenuProvider((menuId, inventory, player) ->
                                                                      ChestMenu.threeRows(menuId, inventory, container),
                                                              Component.literal("SG tooltip acceptance")));
                        return expected.toString();
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
                return;
            }
            if (!seed.isDone()) {
                return;
            }
            if (manifest == null) {
                manifest = JsonParser.parseString(seed.get()).getAsJsonArray();
            }
            if (!(client.player.containerMenu instanceof ChestMenu)) {
                return;
            }

            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (int index = 0; index < manifest.size(); index++) {
                ItemStack expected = ItemStack.CODEC.parse(ops, manifest.get(index)).getOrThrow();
                if (!ItemStack.matches(expected, client.player.containerMenu.getSlot(index).getItem())) {
                    return;
                }
            }
            run(client);
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(OUTPUT, failure);
            finished = true;
            client.stop();
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
            if (!VARIANT.equals("normal")) {
                runFault(client, report);
                complete(client, report);
                return;
            }
            for (boolean simple : List.of(false, true)) {
                vanilla.getClass().getMethod("set", Object.class).invoke(vanilla, simple);
                // Distinct formatting branches, not the spike's full Cartesian product.
                for (int mode : simple ? new int[]{0, 7} : new int[]{0, 1, 2, 3, 4, 7}) {
                    boolean ctrl = (mode & 1) != 0;
                    boolean shift = (mode & 2) != 0;
                    boolean alt = (mode & 4) != 0;
                    input(ctrl, shift, alt, false);
                    require((boolean) keys.getMethod("isDisplayPropertiesDown").invoke(null) == ctrl
                                    && (boolean) keys.getMethod("isDisplayConstructionDown").invoke(null) == alt
                                    && (boolean) keys.getMethod("isDisplayTraitDescriptionsDown").invoke(null) == shift,
                            "Native key tracker did not read controlled window input");
                    for (int index = 0; index < manifest.size(); index++) {
                        compare(client,
                                client.player.containerMenu.getSlot(index).getItem(),
                                mode == 7 ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL,
                                true);
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
            properties.setKey(oldKeys.get(0));
            traits.setKey(oldKeys.get(1));
            construction.setKey(oldKeys.get(2));
            input(false, false, false, false);
            compare(client, client.player.containerMenu.getSlot(4).getItem(), TooltipFlag.ADVANCED, true);
            ItemStack normal = client.player.containerMenu.getSlot(2).getItem();
            var data = ModContent.getMixedBootsData(normal).orElseThrow();
            var props = NeoForgeSilentGearTest.NativeApi.component("GEAR_PROPERTIES");
            var parts = NeoForgeSilentGearTest.NativeApi.component("GEAR_CONSTRUCTION");
            var visible = SilentGearNativeTooltipControl.details(client, data.left().source(), TooltipFlag.NORMAL);
            require(visible.size() >= 2, "Visibility control lacks native hints/traits");
            for (int hidden = 1; hidden <= 4; hidden++) {
                ItemStack source = data.left().source();
                TooltipDisplay display = TooltipDisplay.DEFAULT;
                if ((hidden & 1) != 0) {
                    display = display.withHidden(props, true);
                }
                if ((hidden & 2) != 0) {
                    display = display.withHidden(parts, true);
                }
                if (hidden == 4) {
                    display = new TooltipDisplay(true, display.hiddenComponents());
                }
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

            ItemStack absent = data.left().source();
            absent.remove(props);
            require(SilentGearNativeTooltipControl.details(client, absent, TooltipFlag.NORMAL).isEmpty(), "Missing native cache reached formatters");
            compare(client, replace(normal, absent), TooltipFlag.NORMAL, false);
            absent = data.left().source();
            absent.remove(parts);
            compare(client, replace(normal, absent), TooltipFlag.NORMAL, false);
            ItemStack invalid = malformed(data.left().source());
            for (int attempt = 0; attempt < 2; attempt++) {
                compare(client, replace(normal, invalid), TooltipFlag.NORMAL, false, Set.of(0));
            }
            compare(client, normal, TooltipFlag.NORMAL, true);
            report.append("malformed/missing source cache: only affected block omitted; healthy opposite source and later valid blocks retained\n");
            ItemStack broken = data.left().source();
            broken.setDamageValue(broken.getMaxDamage());
            require(capture(client, broken, TooltipFlag.NORMAL, false).stream().anyMatch(line -> translation(line, "misc.silentgear.broken")),
                    "Native Broken diagnostic control missing");
            compare(client, replace(normal, broken), TooltipFlag.NORMAL, false);
            require(SilentGearNativeTooltipControl.details(client, broken, TooltipFlag.NORMAL)
                                                  .stream()
                                                  .noneMatch(line -> translation(line, "misc.silentgear.broken")),
                    "Control imported native Broken diagnostic");
            ItemStack hiddenRoot = normal.copy();
            hiddenRoot.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
            require(capture(client, hiddenRoot, TooltipFlag.NORMAL, false).equals(capture(client, hiddenRoot, TooltipFlag.NORMAL, true)),
                    "Hidden root leaked production details");
            report.append("compact/expanded visibility, Broken/root hiding: unrelated details retained\n");
            complete(client, report);
        } finally {
            properties.setKey(oldKeys.get(0));
            traits.setKey(oldKeys.get(1));
            construction.setKey(oldKeys.get(2));
            traits.setDown(false);
            pressed = null;
            presentation = true;
            expanded = false;
            faultActive = false;
            SilentGearTraitSpike.tooltipObservation = null;
            vanilla.getClass().getMethod("set", Object.class).invoke(vanilla, oldVanilla);
        }
    }

    private static void compare(Minecraft client, ItemStack root, TooltipFlag flag, boolean nativeControl) throws Exception {
        compare(client, root, flag, nativeControl, Set.of());
    }

    private static void compare(Minecraft client, ItemStack root, TooltipFlag flag, boolean nativeControl, Set<Integer> omitted) throws Exception {
        ItemStack before = root.copy();
        SilentGearTraitSpike.tooltipObservation = Thread.currentThread();
        try {
            var baseline = capture(client, root, flag, false);
            var expected = new ArrayList<>(baseline);
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            var sources = List.of(data.left().source(), data.right().source());
            for (int side = 0; side < 2; side++) {
                ItemStack source = sources.get(side);
                List<Component> block = omitted.contains(side) ? List.of() : SilentGearNativeTooltipControl.details(client, source, flag);
                if (!block.isEmpty() && nativeControl) {
                    var nativeLines = capture(client, source, flag, false);
                    require(nativeLines.size() > block.size() && nativeLines.subList(1, 1 + block.size()).equals(block),
                            "Native control differs from complete native event output: " + source.getHoverName().getString());
                }
                int header = side(expected, side == 0 ? "left" : "right");
                require(header >= 0, "Existing side identity disappeared");
                expected.addAll(header + 1 + (flag.isAdvanced() ? 1 : 0), block);
            }

            var actual = capture(client, root, flag, true);
            require(expected.equals(actual), "Mixed order/style/root text differs from isolated native source insertion");
            if (sources.stream().anyMatch(source -> BuiltInRegistries.ITEM.getKey(source.getItem()).getNamespace().equals("justdirethings"))) {
                require(actual.stream().filter(line -> translation(line, "justdirethings.festored")).count() == 1
                                && actual.stream()
                                         .filter(line -> translation(line, "justdirethings.enabled") || translation(line, "justdirethings.disabled"))
                                         .count() == 1,
                        "JDT coexistence lacks its single actual root energy/global-toggle block");
                var energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
                require(energy != null && energy.getAmountAsInt() > 0, "JDT coexistence lacks a live charged root");
                JustDireTooltipTestSupport.energy(actual, energy.getAmountAsInt(), energy.getCapacityAsInt(), expanded);
                require(actual.stream().anyMatch(line -> translation(line, "justdirethings.shiftmoreinfo")) != expanded
                                && actual.stream().anyMatch(line -> translation(line, "justdirethings.ability.jumpboost")) == expanded,
                        "JDT root hint/ability expansion ignored actual controlled Shift");
            }
            require(ItemStack.matches(before, root), "Tooltip mutated root/source/live state");
            if (comparisons == 0) {
                NetworkAcceptanceFiles.write(OUTPUT + "-compact.txt",
                                             actual.stream().map(Component::getString).collect(java.util.stream.Collectors.joining("\n")));
            }
            maxLines = Math.max(maxLines, actual.size());
            for (Component line : actual) {
                maxWidth = Math.max(maxWidth, client.font.width(line));
            }
            comparisons++;
        } finally {
            SilentGearTraitSpike.tooltipObservation = null;
        }
    }

    private static List<Component> capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean production) {
        presentation = production;
        events = 0;
        capturing = true;
        try {
            var lines = JustDireTooltipTestSupport.capture(client, stack, flag, expanded);
            require(events <= 1 && (stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip() || events == 1),
                    "Missing or recursively replayed final tooltip event: " + events);
            if (!ModContent.isMixedBoots(stack)) {
                maxNativeLines = Math.max(maxNativeLines, lines.size());
            }
            captures++;
            return lines;
        } finally {
            capturing = false;
        }
    }

    private static void input(boolean ctrl, boolean shift, boolean alt, boolean rebound) {
        expanded = shift && !rebound;
        pressed = new HashSet<>();
        if (ctrl) {
            pressed.add(rebound ? GLFW.GLFW_KEY_P : GLFW.GLFW_KEY_LEFT_CONTROL);
        }
        if (shift) {
            pressed.add(rebound ? GLFW.GLFW_KEY_T : GLFW.GLFW_KEY_LEFT_SHIFT);
        }
        if (alt) {
            pressed.add(rebound ? GLFW.GLFW_KEY_C : GLFW.GLFW_KEY_LEFT_ALT);
        }
        traits.setDown(shift);
    }

    private static void runFault(Minecraft client, StringBuilder report) throws Exception {
        vanilla.getClass().getMethod("set", Object.class).invoke(vanilla, false);
        ItemStack gameplay = client.player.containerMenu.getSlot(4).getItem();
        ItemStack before = gameplay.copy();
        var expectedTraits = SilentGearCompatibility.effectiveTraits(gameplay).stream().map(trait -> trait.id() + ":" + trait.level()).toList();
        require(!expectedTraits.isEmpty(), "Presentation fault lacks independent completed SG trait-query control");
        faultActive = true;
        input(true, true, true, false);
        for (int attempt = 0; attempt < 3; attempt++) {
            compare(client, client.player.containerMenu.getSlot(0).getItem(), TooltipFlag.ADVANCED, false, Set.of(0, 1));
        }
        for (boolean shift : List.of(false, true)) {
            input(false, shift, true, false);
            for (int index : new int[]{8, 9}) {
                compare(client, client.player.containerMenu.getSlot(index).getItem(), TooltipFlag.NORMAL, false, Set.of(0, 1));
            }
        }
        require(faultCalls == 1, "Presentation refusal retried native binding/formatting: " + faultCalls);
        faultActive = false;
        compare(client, gameplay, TooltipFlag.NORMAL, false, Set.of(0, 1));
        require(SilentGearCompatibility.effectiveTraits(gameplay)
                                       .stream()
                                       .map(trait -> trait.id() + ":" + trait.level())
                                       .toList()
                                       .equals(expectedTraits)
                        && ItemStack.matches(before, gameplay),
                "Presentation failure revoked gameplay query or changed source/root/live state");
        report.append(VARIANT)
              .append(" fault: one attempt; repeated hover latched; no partial source block; ordinary/root/JDT compact+Shift intact; independent SG query retained\n");
    }

    private static ItemStack malformed(ItemStack source) throws Exception {
        source = source.copy();
        var component = NeoForgeSilentGearTest.NativeApi.component("GEAR_CONSTRUCTION");
        Object construction = source.get(component);
        var raw = new ArrayList<Object>((Collection<?>) construction.getClass().getMethod("parts").invoke(construction));
        Class<?> resource = Class.forName("net.silentchaos512.gear.api.util.DataResource");
        Object absent = resource.getMethod("part", String.class).invoke(null, "silentgear:mismatchedboots_test_missing_part");
        Class<?> part = Class.forName("net.silentchaos512.gear.gear.part.PartInstance");
        Object invalid = part.getMethod("of", resource).invoke(null, absent);
        require(!(boolean) part.getMethod("isValid").invoke(invalid), "Malformed-source control resolved unexpectedly");
        raw.add(invalid);
        Class<?> parts = Class.forName("net.silentchaos512.gear.api.part.PartList");
        Object frozen = parts.getMethod("immutable", Collection.class).invoke(null, raw);
        source.set(component, construction.getClass().getConstructor(frozen.getClass()).newInstance(frozen));
        return source;
    }

    private static void complete(Minecraft client, StringBuilder report) {
        require(SilentGearTraitSpike.tooltipRecalculations == 0, "Native recalculation observed during source presentation");
        report.append("full captures=").append(captures).append(" structured mixed comparisons=").append(comparisons)
              .append(" recalculations=").append(SilentGearTraitSpike.tooltipRecalculations).append('\n');
        report.append("layout measurement: maxLines=")
              .append(maxLines)
              .append(" nativeMaxLines=")
              .append(maxNativeLines)
              .append(" maxWidth=")
              .append(maxWidth)
              .append(" scaledWindow=")
              .append(client.getWindow().getGuiScaledWidth())
              .append('x')
              .append(client.getWindow().getGuiScaledHeight())
              .append("; no manual visual acceptance\n");
        NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", report.toString());
        NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "neoforge\n");
    }

    private static ItemStack replace(ItemStack root, ItemStack source) {
        ItemStack copy = root.copy();
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        copy.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(source), data.right()));
        return copy;
    }

    private static int side(List<Component> lines, String side) {
        for (int index = 0; index < lines.size(); index++) {
            if (translation(lines.get(index), "tooltip.mismatchedboots.mixed_boots." + side)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean translation(Component line, String key) {
        if (line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
            return true;
        }
        return line.getSiblings().stream().anyMatch(child -> translation(child, key));
    }

    private static ItemStack swap(NeoForgeSilentGearTest.NativeApi api, Object recipe, ItemStack base, ItemStack addition) throws Exception {
        return (ItemStack) api.assemble.invoke(recipe, net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(base.copy(), addition)));
    }

    private static ItemStack nativeItem(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow().value());
    }
}
