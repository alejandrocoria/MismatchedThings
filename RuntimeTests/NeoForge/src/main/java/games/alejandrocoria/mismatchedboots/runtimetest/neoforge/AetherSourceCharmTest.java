package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.install;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;

/** Production gather/factory route, native paid reinforcement and disposable render-payload mutations. */
final class AetherSourceCharmTest {
    private static final String NATIVE = "com.aetherteam.aetherii.client.renderer.item.tooltip.ClientCharmTooltip";
    private static final boolean SOURCE_FAULT = AetherTooltipTestControl.VARIANT.equals("charmpopulatedfailure");
    private static final boolean FACTORY_FAULT = AetherTooltipTestControl.VARIANT.startsWith("charmfactory");

    private AetherSourceCharmTest() {}

    static ItemStack reinforced(ServerPlayer owner, AetherIICompatibilityTest.NativeApi api) throws Exception {
        var input = item("arkenium_boots"); var original = AetherIICompatibilityTest.snapshot(owner.level().getServer(), input);
        var menu = api.forge(owner); menu.getSlot(0).set(input.copy());
        menu.getClass().getMethod("registerUpdater", Consumer.class).invoke(menu, (Consumer<ItemStack>) result -> menu.getSlot(0).set(result));
        var tierClass = Class.forName("com.aetherteam.aetherii.item.components.ReinforcementTier");
        for (String name : List.of("FIRST", "SECOND", "THIRD")) {
            Object tier = tierClass.getField(name).get(null); int number = (int) tierClass.getMethod("getTierNumber").invoke(tier);
            var access = owner.registryAccess();
            int primary = (int) tierClass.getMethod("getPrimaryCostForTier", net.minecraft.core.RegistryAccess.class, ItemStack.class, int.class)
                    .invoke(null, access, menu.getSlot(0).getItem(), number);
            int secondary = (int) tierClass.getMethod("getSecondaryCostForTier", net.minecraft.core.RegistryAccess.class, ItemStack.class, int.class)
                    .invoke(null, access, menu.getSlot(0).getItem(), number);
            require(primary > 0 && secondary >= 0, "Invalid native reinforcement cost");
            var plates = item("arkenium_plate"); plates.setCount(primary); menu.getSlot(1).set(plates);
            var crystals = secondary == 0 ? ItemStack.EMPTY : item("corrobonite_crystal");
            if (!crystals.isEmpty()) { crystals.setCount(secondary); } menu.getSlot(2).set(crystals);
            require((int) menu.getClass().getMethod("getTierForMaterials", net.minecraft.core.RegistryAccess.class).invoke(menu, access) >= number,
                    "Native material admission rejected reinforcement");
            require((boolean) menu.getClass().getMethod("upgradeItem", net.minecraft.core.RegistryAccess.class, tierClass).invoke(menu, access, tier),
                    "Native reinforcement transaction rejected");
            require(menu.getSlot(0).getItem().get(component("reinforcement_tier")) == tier
                    && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Native reinforcement did not pay/update tier");
        }
        var reinforced = menu.getSlot(0).getItem().copy();
        require(original.equals(AetherIICompatibilityTest.snapshot(owner.level().getServer(), input)), "Reinforcement mutated native input/defaults");
        var result = install(owner, api, reinforced, "health", 0, true);
        result.set(DataComponents.CUSTOM_NAME, Component.literal("Reinforced original"));
        result.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Original lore retained"))));
        return result;
    }

    static int expectedGroups(ItemStack root) throws Exception {
        if (!ModContent.isMixedBoots(root)) { return root.has(component("charms")) ? 1 : 0; }
        if (SOURCE_FAULT || FACTORY_FAULT || root.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip()) { return 0; }
        int groups = 0;
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        for (var source : List.of(data.left().source(), data.right().source())) {
            var display = source.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
            int count = installed(source).size();
            if (!display.hideTooltip() && display.shows(component("charms")) && count > 0 && count <= 4) { groups++; }
        }
        return groups;
    }

    static void verify(Minecraft client, ItemStack root, List<Component> lines) throws Exception {
        String before = encoded(client, root);
        var components = gather(client, root, lines);
        var nativeComponents = components.stream().filter(value -> value.getClass().getName().equals(NATIVE)).toList();
        require(nativeComponents.size() == expectedGroups(root), "Wrong installed-only source/native rich groups");
        require(before.equals(encoded(client, root)), "Native gather/factory mutated root/history");
    }

    static void run(Minecraft client, List<ItemStack> samples, StringBuilder report) throws Exception {
        for (int index : List.of(24, 25, 30, 31, 32)) {
            var root = samples.get(index); var lines = AetherTooltipTestControl.capture(client, root, TooltipFlag.ADVANCED, false).lines();
            verify(client, root, lines);
            if (index == 30) {
                require(lines.stream().anyMatch(line -> key(line, "aether_ii.tooltip.item.reinforcement")), "Paid native reinforcement tier missing");
                continue;
            }
            require(!root.has(component("charms")), "Presentation restored root charm holders");
            require(lines.stream().filter(line -> line.getString().equals("mismatchedboots:mixed_boots")).count() == 1
                    && lines.stream().filter(line -> key(line, "item.components")).count() == 1,
                    "Source graphics duplicated/removed advanced root diagnostics");
            double health = root.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.MAX_HEALTH))
                    .mapToDouble(entry -> entry.modifier().amount()).sum();
            // The paid native Health I charm supplies +2: (4 + 2)/2 = 3, or (2 + 0)/2 = 1.
            AetherIICompatibilityTest.close(health, index < 30 ? 3 : 1, "Snapshot Health unchanged by source icons");
            if (SOURCE_FAULT) {
                require(lines.stream().noneMatch(line -> line.getString().contains(item("charm_of_health_1").getHoverName().getString())),
                        "A populated source failure published a partial charm group");
            }
            String before = encoded(client, root); String defaults = encoded(client, item("neptune_boots")) + encoded(client, item("arkenium_boots"));
            var components = gather(client, root, lines);
            int left = rawIndex("tooltip.mismatchedboots.mixed_boots.left"), right = rawIndex("tooltip.mismatchedboots.mixed_boots.right");
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            int group = 0;
            for (int side = 0; side < 2; side++) {
                var source = side == 0 ? data.left().source() : data.right().source();
                var stacks = installed(source); if (stacks.isEmpty() || SOURCE_FAULT) { continue; }
                if (!FACTORY_FAULT) {
                    var rich = components.stream().filter(value -> value.getClass().getName().equals(NATIVE)).toList().get(group++);
                    var holders = (List<?>) rich.getClass().getMethod("charmHolders").invoke(rich);
                    require(holders.size() == stacks.size(), "Empty capacity or duplicate installed item lost");
                    for (int i = 0; i < stacks.size(); i++) {
                        require(ItemStack.matches(stacks.get(i), stack(holders.get(i))), "Native payload changed installed item order");
                    }
                    int position = preparedIndex(rich);
                    require(position > (side == 0 ? left : right) && (side != 0 || position < right), "Charm grid lost source side");
                    // Mutate only actual disposable native renderer inputs, never the component/history inputs.
                    stack(holders.getFirst()).shrink(1);
                    holders.getFirst().getClass().getMethod("setStack", ItemStack.class).invoke(holders.getFirst(), ItemStack.EMPTY);
                    ((ItemStack) rich.getClass().getMethod("base").invoke(rich)).setCount(3);
                } else {
                    long names = gathered.subList(side == 0 ? left + 1 : right + 1, side == 0 ? right : gathered.size()).stream()
                            .filter(value -> value.left().filter(text -> text.getString().equals(stacks.getFirst().getHoverName().getString())).isPresent()).count();
                    require(names == stacks.size(), "Factory fallback lost installed names/duplicates or source side");
                }
            }
            require(before.equals(encoded(client, root)) && defaults.equals(encoded(client, item("neptune_boots")) + encoded(client, item("arkenium_boots"))),
                    "Render payload shares root/history/native defaults");
            var copiedText = lines.stream().map(line -> (Component) line.copy()).toList();
            require(gather(client, root, copiedText).stream().noneMatch(value -> value.getClass().getName().equals(NATIVE)), "Text-only copy acquired phantom graphics");
            if (!SOURCE_FAULT) { require(copiedText.stream().anyMatch(line -> line.getString().contains(item("charm_of_health_1").getHoverName().getString())), "Text-only fallback lost native name"); }
            if (index >= 31) {
                require(lines.stream().anyMatch(line -> line.getString().contains("Reinforced original"))
                        && lines.stream().anyMatch(line -> line.getString().equals("Original lore retained")), "Native names/lore lost");
            }
        }
        var root = samples.get(24); var data = ModContent.getMixedBootsData(root).orElseThrow();
        // Explicit root-holder override stays on Aether's native route, separate from the two source markers.
        var override = root.copy(); override.set(component("charms"), data.right().source().get(component("charms")));
        var overrideLines = AetherTooltipTestControl.capture(client, override, TooltipFlag.NORMAL, false).lines();
        require(gather(client, override, overrideLines).stream().filter(value -> value.getClass().getName().equals(NATIVE)).count()
                == (SOURCE_FAULT || FACTORY_FAULT ? 1 : 3), "Native root-holder override grid was replaced/removed");
        for (boolean hideAll : List.of(false, true)) {
            var source = data.left().source();
            source.set(DataComponents.TOOLTIP_DISPLAY, hideAll ? new TooltipDisplay(true, new java.util.LinkedHashSet<>())
                    : TooltipDisplay.DEFAULT.withHidden(component("charms"), true));
            var hidden = withSources(root, source, data.right().source());
            var hiddenLines = AetherTooltipTestControl.capture(client, hidden, TooltipFlag.ADVANCED, false).lines();
            verify(client, hidden, hiddenLines);
            if (!AetherTooltipTestControl.VARIANT.equals("tierchanged")) {
                require(hiddenLines.stream().filter(line -> key(line, "aether_ii.tooltip.item.reinforcement")).count() == (hideAll ? 1 : 2),
                        "Hiding source charms unexpectedly hid its independent tier");
            }
        }
        // A synthetic >4 installed source measures only the known native layout boundary, not upgrade eligibility.
        var source = data.left().source(); var nativeCharms = source.get(component("charms"));
        Object holder = ((List<?>) nativeCharms.getClass().getMethod("charmHolders").invoke(nativeCharms)).getFirst();
        source.set(component("charms"), nativeCharms.getClass().getConstructor(List.class).newInstance(List.of(holder, holder, holder, holder, holder)));
        var crowded = withSources(root, source, data.right().source());
        var crowdedLines = AetherTooltipTestControl.capture(client, crowded, TooltipFlag.NORMAL, false).lines();
        verify(client, crowded, crowdedLines);
        if (!SOURCE_FAULT) {
            long names = gathered.subList(rawIndex("tooltip.mismatchedboots.mixed_boots.left") + 1,
                            rawIndex("tooltip.mismatchedboots.mixed_boots.right")).stream()
                    .filter(value -> value.left().filter(text -> text.getString().equals(item("charm_of_health_1").getHoverName().getString())).isPresent()).count();
            require(names == 5, "Oversize source did not render all five native names under its own side");
        }
        var nativePayload = (TooltipComponent) Class.forName(NATIVE + "$CharmTooltip").getConstructor(ItemStack.class, List.class)
                .newInstance(item("neptune_boots"), List.of());
        var foreign = Either.<FormattedText, TooltipComponent>right(nativePayload);
        var event = new RenderTooltipEvent.GatherComponents(ItemStack.EMPTY, 960, 540, new ArrayList<>(List.of(foreign)), -1);
        NeoForge.EVENT_BUS.post(event);
        require(event.getTooltipElements().size() == 1 && event.getTooltipElements().getFirst() == foreign, "Unrelated rich tooltip modified");
        report.append("CHARM GRAPHICS paid Arkenium FIRST/SECOND/THIRD + installed holder; both orientations; duplicate 2/1 sources; empty capacity omitted; ")
                .append("native prepared factory; side-local visibility/names/lore; text-only and >4 fallback; root override/foreign rich intact; disposable payload mutation preserves history/defaults; variant=")
                .append(AetherTooltipTestControl.VARIANT).append('\n');
    }

    static List<Either<FormattedText, TooltipComponent>> gathered = List.of();
    static void observe(RenderTooltipEvent.GatherComponents event) { gathered = List.copyOf(event.getTooltipElements()); }
    private static List<ClientTooltipComponent> gather(Minecraft client, ItemStack root, List<Component> lines) {
        return ClientHooks.gatherTooltipComponents(root, lines, 20, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.font);
    }
    private static List<ItemStack> installed(ItemStack source) throws Exception {
        Object charms = source.get(component("charms")); if (charms == null) { return List.of(); }
        var result = new ArrayList<ItemStack>();
        for (Object holder : (List<?>) charms.getClass().getMethod("charmHolders").invoke(charms)) { var stack = stack(holder); if (!stack.isEmpty()) { result.add(stack); } }
        return result;
    }
    private static ItemStack stack(Object holder) throws Exception { return (ItemStack) holder.getClass().getMethod("getStack").invoke(holder); }
    private static ItemStack withSources(ItemStack root, ItemStack left, ItemStack right) {
        var copy = root.copy(); copy.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(left), new BootHalf(right))); return copy;
    }
    private static String encoded(Minecraft client, ItemStack root) { return AetherTooltipTestControl.encoded(client, root); }
    private static boolean key(FormattedText text, String prefix) {
        return text instanceof Component line && (line.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith(prefix)
                || line.getSiblings().stream().anyMatch(child -> key(child, prefix)));
    }
    private static int rawIndex(String key) {
        for (int i = 0; i < gathered.size(); i++) { if (gathered.get(i).left().filter(text -> key(text, key)).isPresent()) { return i; } } return -1;
    }
    private static int preparedIndex(ClientTooltipComponent component) throws Exception {
        for (int i = 0; i < gathered.size(); i++) {
            var value = gathered.get(i).right().orElse(null);
            if (value != null && value.getClass().getName().endsWith("AetherSourceCharms$Prepared")) {
                var method = value.getClass().getDeclaredMethod("component"); method.setAccessible(true);
                if (method.invoke(value) == component) { return i; }
            }
        }
        throw new AssertionError("Missing self-contained prepared bridge");
    }
}
