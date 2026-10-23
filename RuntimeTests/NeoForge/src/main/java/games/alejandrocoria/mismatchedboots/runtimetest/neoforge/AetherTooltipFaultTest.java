package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.aether.AetherTooltipState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.ClientHooks;

import java.util.List;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;

/** Two real equipment scenes, rather than duplicating every family for each presentation fault. */
final class AetherTooltipFaultTest {
    static final List<String> VARIANTS = List.of("setbindingfailure", "tierbindingfailure", "charmbindingfailure", "setpopulatedfailure", "tierpopulatedfailure");
    private AetherTooltipFaultTest() {}
    static boolean active() { return VARIANTS.contains(AetherTooltipTestControl.VARIANT); }
    static void verify(Minecraft client, List<ItemStack> samples, StringBuilder report) throws Exception {
        boolean sets = !AetherTooltipTestControl.VARIANT.startsWith("set");
        boolean tiers = !AetherTooltipTestControl.VARIANT.startsWith("tier");
        boolean graphics = !AetherTooltipTestControl.VARIANT.equals("charmbindingfailure");
        var playerRows = AetherIITooltipClientTest.nativeSetRows(client, TooltipFlag.NORMAL);
        var worn = client.player.getItemBySlot(EquipmentSlot.FEET);
        require(playerRows.size() == 2 && AetherTooltipState.attributesReady() && AetherTooltipState.tiersReady(), "Presentation fault changed gameplay/containment readiness");
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            for (int repeat = 0; repeat < 3; repeat++) {
                var actual = AetherTooltipTestControl.capture(client, worn, flag, false);
                var idle = AetherTooltipTestControl.capture(client, worn.copy(), flag, false);
                var expectedRows = AetherIITooltipClientTest.nativeSetRows(client, flag);
                require(actual.nativeAttributes() == 0 && idle.nativeAttributes() == 0, "Formatter failure re-enabled borrowed native rows");
                for (var row : expectedRows) {
                    require(actual.lines().stream().filter(row::equals).count() == (sets ? 1 : 0), "Wrong bounded set formatter output");
                    require(!idle.lines().contains(row), "Failed formatter borrowed worn values for a copy");
                }
                var source = samples.get(24);
                var lines = AetherTooltipTestControl.capture(client, source, flag, false).lines();
                require(lines.stream().filter(line -> key(line, "aether_ii.tooltip.item.reinforcement")).count() == (tiers ? 2 : 0), "Wrong independent tier omission");
                require(lines.stream().filter(line -> key(line, "item.aether_ii.neptune_boots.nitrogen_internals.ability.tooltip.1")).count() == (sets ? 2 : 0),
                        "Partial set scratch block escaped or healthy set text disappeared");
                require(lines.stream().filter(line -> key(line, "tooltip.mismatchedboots.mixed_boots.left")
                        || key(line, "tooltip.mismatchedboots.mixed_boots.right")).count() == 2, "Optional fault removed source identities");
                require(rich(client, source, lines) == (graphics ? 2 : 0), "Binding fault crossed presentation domains");
                if (!graphics) {
                    long names = AetherSourceCharmTest.gathered.stream().filter(value -> value.left().filter(text -> text.getString().equals(
                            AetherIICompatibilityTest.item("charm_of_health_1").getHoverName().getString())).isPresent()).count();
                    require(names == 3, "Missing native binding did not preserve all source names");
                }
                // Ordinary root numbers remain authoritative even when optional set/tier groups disappear.
                require(lines.stream().anyMatch(line -> key(line, "item.modifiers.feet"))
                        && source.get(DataComponents.ATTRIBUTE_MODIFIERS) != null, "Optional fault removed ordinary composed attributes");
                var original = samples.get(30);
                var nativeLines = AetherTooltipTestControl.capture(client, original, flag, false).lines();
                require(rich(client, original, nativeLines) == 1 && nativeLines.stream().anyMatch(line -> key(line, "aether_ii.tooltip.item.reinforcement")),
                        "Optional failure changed original native tier/charm rendering");
            }
        }
        require(playerRows.equals(AetherIITooltipClientTest.nativeSetRows(client, TooltipFlag.NORMAL)), "Presentation failure changed native player values");
        if (AetherTooltipTestControl.VARIANT.equals("setpopulatedfailure")) { require(AetherTooltipTestControl.setScratchChecked, "Populated set fault never ran"); }
        if (AetherTooltipTestControl.VARIANT.equals("tierpopulatedfailure")) { require(AetherTooltipTestControl.tierScratchCalls == 1, "Tier provider was retried/never faulted"); }
        report.append("FOCUSED FAULT ").append(AetherTooltipTestControl.VARIANT)
                .append(": native control then actual worn Burrukai; two real scenes, repeated normal/advanced; no partial/borrowed rows; independent tiers/graphics/native originals; immutable histories/player values\n");
    }
    private static long rich(Minecraft client, ItemStack root, List<Component> lines) {
        String before = AetherTooltipTestControl.encoded(client, root);
        var components = ClientHooks.gatherTooltipComponents(root, lines, 20, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.font);
        require(before.equals(AetherTooltipTestControl.encoded(client, root)), "Faulted gather changed root/history");
        return components.stream().filter(value -> value.getClass().getName().endsWith("ClientCharmTooltip")).count();
    }
    private static boolean key(Component line, String prefix) {
        return line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text && text.getKey().startsWith(prefix)
                || line.getSiblings().stream().anyMatch(child -> key(child, prefix));
    }
}
