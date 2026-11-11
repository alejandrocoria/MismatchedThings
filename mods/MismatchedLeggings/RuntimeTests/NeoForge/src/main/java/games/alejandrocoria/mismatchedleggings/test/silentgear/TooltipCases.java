package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.mojang.blaze3d.platform.InputConstants;
import games.alejandrocoria.mismatchedleggings.client.compatibility.silentgear.SilentGearTooltipClient;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearRecognition;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.setup.SgDataComponents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Presentation boundaries on explicit native controls; no whole gameplay/lifecycle matrix. */
final class TooltipCases {
    private TooltipCases() {
    }

    static void normal(Minecraft client, int count, List<KeyMapping> mappings, Object setting, StringBuilder report) throws Exception {
        setting.getClass().getMethod("set", Object.class).invoke(setting, true);
        for (int mode : new int[]{0, 7}) {
            TooltipClient.input(mode, false);
            for (int i = 0; i < count; i++) {
                TooltipClient.compare(client, item(client, i), mode == 0 ? TooltipFlag.NORMAL : TooltipFlag.ADVANCED);
            }
        }
        setting.getClass().getMethod("set", Object.class).invoke(setting, false);
        mappings.get(0).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P));
        mappings.get(1).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T));
        mappings.get(2).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C));
        var keys = Class.forName("net.silentchaos512.gear.client.KeyTracker");
        for (int mode : new int[]{0, 7}) {
            TooltipClient.input(mode, true);
            require((boolean) keys.getMethod("isDisplayPropertiesDown").invoke(null) == (mode == 7)
                    && (boolean) keys.getMethod("isDisplayTraitDescriptionsDown").invoke(null) == (mode == 7)
                    && (boolean) keys.getMethod("isDisplayConstructionDown").invoke(null) == (mode == 7), "Rebound native tracker failed");
            TooltipClient.compare(client, item(client, 4), mode == 0 ? TooltipFlag.NORMAL : TooltipFlag.ADVANCED);
        }
        mappings.get(0).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_CONTROL));
        mappings.get(1).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT));
        mappings.get(2).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT));
        TooltipClient.input(0, false);
        report.append("Current vanilla style and rebound P/T/C: native queries, hints and structured equality\n");

        var root = item(client, 2); // Lined gold/plain iron: intentionally summary-free native hidden-group oracle.
        var source = TooltipFixtures.sources(root).getFirst();
        var opposite = TooltipFixtures.sources(root).get(1);
        var props = SgDataComponents.GEAR_PROPERTIES.get();
        var parts = SgDataComponents.GEAR_CONSTRUCTION.get();
        var visible = TooltipNativeControl.details(client, source, TooltipFlag.NORMAL);
        require(visible.size() >= 2 && !TooltipNativeControl.details(client, opposite, TooltipFlag.NORMAL).isEmpty(),
                "Visibility control lacks positive native blocks on both sides");
        for (int hidden = 1; hidden <= 4; hidden++) {
            var selectedSource = source.copy();
            var display = TooltipDisplay.DEFAULT;
            if ((hidden & 1) != 0) { display = display.withHidden(props, true); }
            if ((hidden & 2) != 0) { display = display.withHidden(parts, true); }
            if (hidden == 4) { display = new TooltipDisplay(true, display.hiddenComponents()); }
            selectedSource.set(DataComponents.TOOLTIP_DISPLAY, display);
            var selected = TooltipNativeControl.details(client, selectedSource, TooltipFlag.NORMAL);
            var expected = hidden == 1 ? visible.subList(visible.size() - 1, visible.size())
                    : hidden == 2 ? visible.subList(0, visible.size() - 1) : List.of();
            require(selected.equals(expected), "Hidden native group leaked a compact trait or hint: " + hidden);
            var probe = TooltipFixtures.replace(root, selectedSource);
            TooltipClient.compare(client, probe, TooltipFlag.NORMAL, false, Set.of());
            TooltipClient.input(7, false);
            var expanded = TooltipNativeControl.details(client, selectedSource, TooltipFlag.ADVANCED);
            require(expanded.stream().anyMatch(line -> TooltipClient.translation(line, "misc.silentgear.tooltip.properties")) == (hidden == 2)
                    && expanded.stream().anyMatch(line -> TooltipClient.translation(line, "misc.silentgear.tooltip.construction")) == (hidden == 1),
                    "Expanded hidden group differs: " + hidden);
            TooltipClient.compare(client, probe, TooltipFlag.ADVANCED, false, Set.of());
            TooltipClient.input(0, false);
        }
        var missing = source.copy();
        missing.remove(props);
        var missingRoot = TooltipFixtures.replace(root, missing);
        require(!TooltipFixtures.sources(missingRoot).getFirst().has(props), "Missing-properties fixture was repaired before hover");
        TooltipClient.compare(client, missingRoot, TooltipFlag.NORMAL, false, Set.of());
        // NeoForge's pre-existing advanced durability appender can ask SG to rebuild missing properties.
        // Check our optional appender in isolation here; do not turn this profile into source-cache repair.
        var beforeMissing = missing.copy();
        var omitted = new ArrayList<Component>();
        TooltipObservation.active = true;
        try {
            SilentGearTooltipClient.appendSource(missing, TooltipFlag.ADVANCED, omitted::add);
        } finally {
            TooltipObservation.active = false;
        }
        require(omitted.isEmpty() && ItemStack.matches(beforeMissing, missing), "Advanced optional appender repaired missing cache");
        missing = source.copy();
        missing.remove(parts);
        missingRoot = TooltipFixtures.replace(root, missing);
        require(!TooltipFixtures.sources(missingRoot).getFirst().has(parts), "Missing-construction fixture was repaired before hover");
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            TooltipClient.compare(client, missingRoot, flag, false, Set.of());
        }
        missing = source.copy();
        missing.set(props, GearPropertiesData.EMPTY);
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            TooltipClient.compare(client, TooltipFixtures.replace(root, missing), flag, false, Set.of(0));
        }
        var malformed = TooltipFixtures.replace(root, TooltipFixtures.invalidParts(source));
        for (int i = 0; i < 2; i++) {
            TooltipClient.compare(client, malformed, TooltipFlag.NORMAL, false, Set.of(0));
        }
        TooltipClient.compare(client, root, TooltipFlag.NORMAL);
        var broken = source.copy();
        broken.setDamageValue(broken.getMaxDamage());
        require(TooltipClient.capture(client, broken, TooltipFlag.NORMAL, false).stream()
                .anyMatch(line -> TooltipClient.translation(line, "misc.silentgear.broken")), "Native Broken control is not positive");
        var brokenRoot = TooltipFixtures.replace(root, broken);
        TooltipClient.compare(client, brokenRoot, TooltipFlag.NORMAL, false, Set.of());
        require(TooltipClient.capture(client, brokenRoot, TooltipFlag.NORMAL, true).stream()
                .noneMatch(line -> TooltipClient.translation(line, "misc.silentgear.broken")), "Native Broken claim leaked into mixed root");
        var hiddenRoot = root.copy();
        hiddenRoot.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        require(TooltipClient.capture(client, hiddenRoot, TooltipFlag.NORMAL, false)
                .equals(TooltipClient.capture(client, hiddenRoot, TooltipFlag.NORMAL, true)), "Hidden root leaked presentation");
        report.append("Compact/expanded group/source/root visibility; missing/empty caches, unresolved parts and Broken provenance; healthy other source/later positives retained\n")
                .append("Missing properties: complete normal tooltip and isolated advanced production appender; legacy advanced durability rebuilding is outside this acceptance\n");
        if (count > 6) {
            report.append("Both current packaged Boots inverse roots matched their own native source block; no duplicate peer event\n");
        }
    }

    static void fault(Minecraft client, int count, StringBuilder report) throws Exception {
        var gameplay = item(client, 2);
        var before = gameplay.copy();
        require(SilentGearRecognition.brilliant(gameplay, client.player, false), "Independent gold-source Brilliant query lacks a positive");
        TooltipClient.faultActive = true;
        for (int attempt = 0; attempt < 3; attempt++) {
            for (int i : new int[]{0, 2, 4}) {
                TooltipClient.compare(client, item(client, i), TooltipFlag.ADVANCED, false, Set.of(0, 1));
            }
        }
        require(TooltipClient.faultCalls == 1, "Presentation retried failed binding/formatter: " + TooltipClient.faultCalls);
        TooltipClient.faultActive = false;
        TooltipClient.input(0, false);
        TooltipClient.compare(client, gameplay, TooltipFlag.NORMAL, false, Set.of(0, 1));
        require(SilentGearRecognition.brilliant(gameplay, client.player, false) && ItemStack.matches(before, gameplay),
                "Presentation refusal changed history or revoked independent Brilliant gameplay");
        for (int i = 6; i < count; i++) {
            TooltipClient.compare(client, item(client, i), TooltipFlag.ADVANCED);
        }
        report.append(TooltipClient.VARIANT).append(" fault: one actual attempt, permanent presentation-only decline; no partial list/retry; native peer/root details and independent Brilliant positive preserved\n");
    }

    private static ItemStack item(Minecraft client, int index) {
        return client.player.containerMenu.getSlot(index).getItem();
    }
}
