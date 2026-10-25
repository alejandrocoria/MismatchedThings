package games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Native text/number formatting, but eligibility and all displayed state belong to the mixed root. */
public final class JustDireTooltipClient {
    private static final List<String> UPGRADES = List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp", "earthquake");
    private static Formats formats;
    private static boolean formatsFailed;

    private JustDireTooltipClient() {
    }

    public static void appendRootState(ItemStack root, TooltipDisplay display, Consumer<Component> tooltip) {
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null || !JustDireSettingsCompatibility.mixed(root)
                || !(JustDireUpgradesCompatibility.nativeBoot(data.left().source())
                || JustDireUpgradesCompatibility.nativeBoot(data.right().source()))) {
            return;
        }
        boolean shift = Minecraft.getInstance().hasShiftDown();
        appendEnergy(root, display, tooltip, shift);
        var global = JustDireTooltipClient.<Boolean>component("tool_enabled");
        if (global != null && display.shows(global)) {
            var line = Component.translatable(root.getOrDefault(global, true) ? "justdirethings.enabled" : "justdirethings.disabled")
                                .withStyle(root.getOrDefault(global, true) ? ChatFormatting.GREEN : ChatFormatting.DARK_RED);
            var mapping = JustDireSettingsClient.toggleMapping();
            if (mapping != null) {
                line.append(Component.literal(" ").append(Component.translatable("justdirethings.presshotkey",
                                                                                 mapping.getKey().getDisplayName())
                                                                   .withStyle(ChatFormatting.DARK_GRAY)));
            }
            tooltip.accept(line);
        }
        var abilities = new ArrayList<Component>();
        for (String name : UPGRADES) {
            var installed = JustDireTooltipClient.<Boolean>component(name + "_upgrade_installed");
            var toggle = JustDireTooltipClient.<Boolean>component(name + "_toggle");
            if (installed == null || toggle == null || !display.shows(installed) || !display.shows(toggle)
                    || JustDireSettingsCompatibility.metadata(root, name) == null) {
                continue;
            }
            var ability = Component.translatable("justdirethings.ability." + name);
            if (!root.getOrDefault(installed, false)) {
                ability.append(Component.translatable("justdirethings.missingupgrade")).withStyle(ChatFormatting.GRAY);
            } else {
                // This describes the saved individual switch, not current global/FE/cooldown readiness.
                ability.withStyle(root.getOrDefault(toggle, true) ? ChatFormatting.GREEN : ChatFormatting.DARK_RED);
            }
            abilities.add(ability);
        }
        var lava = JustDireTooltipClient.<Boolean>component("lavarepair_toggle");
        if (lava != null && display.shows(lava) && JustDireLavaRepairCompatibility.eligible(root)) {
            abilities.add(Component.translatable("justdirethings.ability.lavarepair")
                                   .withStyle(root.getOrDefault(lava, true) ? ChatFormatting.GREEN : ChatFormatting.DARK_RED));
        }
        if (shift) {
            abilities.forEach(tooltip);
        } else if (!abilities.isEmpty()) {
            tooltip.accept(Component.translatable("justdirethings.shiftmoreinfo").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void appendEnergy(ItemStack root, TooltipDisplay display, Consumer<Component> tooltip, boolean shift) {
        var energyType = component("forge_energy");
        if (energyType == null || !display.shows(energyType)) {
            return;
        }
        var energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
        if (energy == null) {
            return;
        }
        String amount = number(energy.getAmountAsInt(), shift), capacity = number(energy.getCapacityAsInt(), shift);
        if (amount != null && capacity != null) {
            tooltip.accept(Component.translatable("justdirethings.festored", amount, capacity).withStyle(ChatFormatting.GREEN));
        }
    }

    private static String number(int value, boolean full) {
        if (formatsFailed) {
            return null;
        }
        try {
            if (formats == null) {
                var helpers = Class.forName("com.direwolf20.justdirethings.util.MagicHelpers");
                var lookup = MethodHandles.publicLookup();
                formats = new Formats(lookup.findStatic(helpers, "formatted", MethodType.methodType(String.class, int.class)),
                                      lookup.findStatic(helpers, "tidyValue", MethodType.methodType(String.class, float.class)));
            }
            return full ? (String) formats.full.invokeExact(value) : (String) formats.shortened.invokeExact((float) value);
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }
            formatsFailed = true;
            Constants.LOG.warn("Just Dire Things tooltip number formatting unavailable; mixed FE text disabled", failure);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", path)).map(holder -> holder.value()).orElse(null);
    }

    private record Formats(MethodHandle full, MethodHandle shortened) {
    }
}
