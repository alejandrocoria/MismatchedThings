package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Source descriptions are provenance; current FE is shown once from the root's production wallet. */
public final class JustDireTooltipCompatibility {
    private static final Set<String> BOOTS = Set.of(
            "ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots");
    private static final Set<String> SOURCE_STATE = Set.of(
            "justdirethings.festored", "justdirethings.enabled", "justdirethings.disabled");
    private static final Set<String> ABILITIES = Set.of(
            "justdirethings.ability.stepheight", "justdirethings.ability.jumpboost",
            "justdirethings.ability.negatefalldamage", "justdirethings.ability.groundstomp",
            "justdirethings.ability.earthquake", "justdirethings.ability.lavarepair");

    private JustDireTooltipCompatibility() {}

    public static Consumer<Component> sourceTooltip(ItemStack source, Consumer<Component> tooltip) {
        Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
        if (!id.getNamespace().equals("justdirethings") || !BOOTS.contains(id.getPath())) { return tooltip; }
        return new Consumer<>() {
            private boolean abilitiesLabel;

            @Override
            public void accept(Component line) {
                if (line.getContents() instanceof TranslatableContents text) {
                    if (SOURCE_STATE.contains(text.getKey())) { return; }
                    if (ABILITIES.contains(text.getKey())) {
                        if (!abilitiesLabel) {
                            tooltip.accept(Component.translatable("tooltip.mismatchedboots.just_dire.source_abilities")
                                    .withStyle(ChatFormatting.DARK_GRAY));
                            abilitiesLabel = true;
                        }
                        // Native green/red indicates source settings, not mixed availability or support.
                        tooltip.accept(line.copy().withStyle(ChatFormatting.GRAY));
                        return;
                    }
                }
                tooltip.accept(line);
            }
        };
    }

    public static void appendEnergy(ItemStack root, TooltipDisplay display, Consumer<Component> tooltip) {
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
