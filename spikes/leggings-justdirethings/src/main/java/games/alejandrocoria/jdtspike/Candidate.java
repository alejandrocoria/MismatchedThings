package games.alejandrocoria.jdtspike;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import java.util.EnumSet;
import java.util.List;

/** Deliberately local feasibility adapter, not a production contract or optional API binding. */
public final class Candidate {
    public static final List<Ability> MOVEMENT = List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED);
    public static boolean enabled = true;

    private Candidate() {
    }

    public static boolean mixed(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(LeggingsContent.MIXED_LEGGINGS_ID);
    }

    public static List<ItemStack> sources(ItemStack root) {
        var data = root.get(LeggingsContent.mixedLeggingsDataType());
        return data == null ? List.of() : List.of(data.left().source(), data.right().source());
    }

    public static ItemStack view(ItemStack root, Ability ability) {
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack source : sources(root)) {
            if (source.getItem() instanceof ToggleableTool tool && tool.hasAbility(ability)) {
                if (best.isEmpty() || tool.getAbilityParams(ability).maxSlider
                        > ((ToggleableTool) best.getItem()).getAbilityParams(ability).maxSlider) {
                    best = source;
                }
            }
        }
        if (best.isEmpty()) {
            return best;
        }
        var result = new ItemStack(best.getItem());
        result.applyComponents(root.getComponents());
        return result;
    }

    public static Item metadata(ItemStack root) {
        // Eclipse is a superset of the other leggings. Per-ability views still own each range.
        for (Ability ability : List.of(Ability.PHASE, Ability.DECOY, Ability.SWIMSPEED, Ability.RUNSPEED)) {
            var view = view(root, ability);
            if (!view.isEmpty()) {
                return view.getItem();
            }
        }
        return root.getItem();
    }

    public static EnumSet<Ability> abilities(ItemStack root) {
        var result = EnumSet.noneOf(Ability.class);
        for (Ability ability : Ability.values()) {
            if (!view(root, ability).isEmpty()) {
                result.add(ability);
            }
        }
        return result;
    }

    public static void movement(Player player) {
        ItemStack root = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!enabled || !mixed(root)) {
            return;
        }
        for (Ability ability : MOVEMENT) {
            ItemStack view = view(root, ability);
            if (!view.isEmpty() && ((ToggleableTool) view.getItem()).canUseAbility(view, ability)) {
                // Native callbacks read transient metadata; they neither charge FE nor mutate this view.
                ability.action.execute(player.level(), player, view);
            }
        }
    }

    public static void attributes(ItemAttributeModifierEvent event) {
        ItemStack root = event.getItemStack();
        if (!enabled || !mixed(root)) {
            return;
        }
        event.removeModifier(JDTRegistration.PHASE, Identifier.fromNamespaceAndPath("justdirethings", "justdirephase"));
        var view = view(root, Ability.PHASE);
        if (!view.isEmpty() && ((ToggleableTool) view.getItem()).canUseAbility(view, Ability.PHASE)
                && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0) >= 50) {
            event.addModifier(JDTRegistration.PHASE,
                    new AttributeModifier(Identifier.fromNamespaceAndPath("justdirethings", "justdirephase"), 1,
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS);
        }
    }
}
