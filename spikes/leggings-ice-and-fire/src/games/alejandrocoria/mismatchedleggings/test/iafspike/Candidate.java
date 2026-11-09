package games.alejandrocoria.mismatchedleggings.test.iafspike;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Happy-path research candidate, not a production final-entry/receiver proof. */
public final class Candidate {
    public static int activations;
    private static Item receiver;

    private Candidate() {}

    public static boolean any(ItemStack root, Predicate<ItemStack> predicate) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        return data != null && (predicate.test(data.left().source()) || predicate.test(data.right().source()));
    }

    public static boolean tide(Entity entity) {
        return entity instanceof Player player && any(player.getItemBySlot(EquipmentSlot.LEGS), source -> {
            var equipped = source.get(DataComponents.EQUIPPABLE);
            return source.getItem().getClass().getName().equals("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem")
                    && equipped != null && equipped.slot() == EquipmentSlot.LEGS;
        });
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.LEGS || !(entity instanceof Player player)
                || player.getItemBySlot(slot) != root || !tide(entity)) {
            return;
        }
        if (receiver == null) {
            receiver = BuiltInRegistries.ITEM.getValue(Identifier.parse("iceandfire:tide_blue_leggings"));
        }
        var carrier = new ItemStack(receiver);
        var equipped = carrier.get(DataComponents.EQUIPPABLE);
        if (!receiver.getClass().getName().equals("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem")
                || equipped == null || equipped.slot() != slot) {
            throw new AssertionError("Fixed Tide LEGS receiver mismatch");
        }
        activations++;
        receiver.inventoryTick(carrier, level, player, slot);
        if (carrier.getCount() != 1 || carrier.getDamageValue() != 0) {
            throw new AssertionError("Native entry mutated disposable carrier");
        }
    }
}
