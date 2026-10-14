package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Supported intrinsic abilities, installed upgrades and live FE describe root, not frozen sources. */
public final class JustDireTooltipCompatibility {
    private static final Set<String> BOOTS = Set.of(
            "ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots");
    private static final Set<String> SOURCE_STATE = Set.of(
            "justdirethings.festored", "justdirethings.enabled", "justdirethings.disabled");
    private static final List<String> UPGRADES = List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp", "earthquake");

    private JustDireTooltipCompatibility() {}

    public static Consumer<Component> sourceTooltip(ItemStack source, Consumer<Component> tooltip) {
        Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
        if (!id.getNamespace().equals("justdirethings") || !BOOTS.contains(id.getPath())) { return tooltip; }
        return line -> {
            if (line.getContents() instanceof TranslatableContents text) {
                if (SOURCE_STATE.contains(text.getKey())) { return; }
                if (UPGRADES.stream().anyMatch(name -> text.getKey().equals("justdirethings.ability." + name))) { return; }
                // No audited boot has remaining Shift-only lore once Lava moves to root.
                if (text.getKey().equals("justdirethings.shiftmoreinfo")
                        || text.getKey().equals("justdirethings.ability.lavarepair")) { return; }
            }
            tooltip.accept(line);
        };
    }

    public static void appendRootState(ItemStack root, TooltipDisplay display, Consumer<Component> tooltip) {
        if (ModContent.getMixedBootsData(root).isEmpty()) { return; }
        appendEnergy(root, display, tooltip);
        DataComponentType<Boolean> lavaToggle = component("lavarepair_toggle");
        if (lavaToggle != null && display.shows(lavaToggle) && JustDireLavaRepairCompatibility.eligible(root)) {
            tooltip.accept(Component.translatable("justdirethings.ability", Component.translatable("justdirethings.ability.lavarepair"),
                    Component.translatable(enabled(root, "tool_enabled") && enabled(root, "lavarepair_toggle")
                            ? "justdirethings.enabled" : "justdirethings.disabled")).withStyle(ChatFormatting.GRAY));
        }
        boolean labeled = false;
        for (String name : UPGRADES) {
            DataComponentType<Boolean> installed = component(name + "_upgrade_installed");
            if (installed == null || !display.shows(installed) || !root.getOrDefault(installed, false)) { continue; }
            if (!labeled) {
                tooltip.accept(Component.translatable("tooltip.mismatchedboots.just_dire.installed_upgrades").withStyle(ChatFormatting.DARK_GRAY));
                labeled = true;
            }
            // This is installation/configuration, not a claim that every native consumer is supported.
            Component ability = Component.translatable("justdirethings.ability." + name);
            Component toggle = Component.translatable("tooltip.mismatchedboots.just_dire." +
                    (enabled(root, "tool_enabled") && enabled(root, name + "_toggle") ? "on" : "off"));
            DataComponentType<Integer> value = component(name + "_value");
            // Earthquake's fixed native numeric field is unused by its effect, not a player setting.
            Component line = !name.equals("earthquake") && value != null && display.shows(value) && root.has(value)
                    ? Component.translatable("tooltip.mismatchedboots.just_dire.upgrade_setting", ability, toggle, root.get(value))
                    : Component.translatable("tooltip.mismatchedboots.just_dire.upgrade", ability, toggle);
            tooltip.accept(line.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean enabled(ItemStack root, String path) {
        DataComponentType<Boolean> type = component(path);
        return type == null || root.getOrDefault(type, true);
    }

    @SuppressWarnings("unchecked") private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", path)).map(holder -> holder.value()).orElse(null);
    }

    private static void appendEnergy(ItemStack root, TooltipDisplay display, Consumer<Component> tooltip) {
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null || !(poweredSource(data.left().source()) || poweredSource(data.right().source()))) { return; }
        var component = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", "forge_energy"));
        if (component.isEmpty() || !display.shows(component.get().value())) { return; }
        var energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
        if (energy == null) { return; }
        // Exact integers in both normal/Shift modes make even small real charging increments visible.
        tooltip.accept(Component.translatable("justdirethings.festored",
                String.format(Locale.ROOT, "%,d", energy.getAmountAsInt()),
                String.format(Locale.ROOT, "%,d", energy.getCapacityAsInt())).withStyle(ChatFormatting.GREEN));
    }

    private static boolean poweredSource(ItemStack source) {
        Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.getNamespace().equals("justdirethings")
                && (id.getPath().equals("celestigem_boots") || id.getPath().equals("eclipsealloy_boots"));
    }
}
