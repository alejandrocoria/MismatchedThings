package games.alejandrocoria.mismatchedleggings.test.refundspike;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Disposable Leggings policy witness; not an API or production component identifier. */
public final class WitnessSupport {
    private WitnessSupport() {}

    @SuppressWarnings("unchecked")
    static DataComponentType<Boolean> flag(String name) {
        return (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(
                Identifier.fromNamespaceAndPath("justdirethings", name + "_upgrade_installed"));
    }

    public static void bind() {
        if (!System.getProperty("refund.spike.fault", "").equals("binding")) {
            LeggingsWitness.bindItems();
        }
    }

    public static void creation(ItemStack root, ItemStack first) {
        LeggingsWitness.initializeCredits(root, JustDireRefundCredits.NAMES.stream()
                .filter(name -> first.getOrDefault(flag(name), false) && root.getOrDefault(flag(name), false)).toList());
    }

    public static void installed(ItemStack root, String name) {
        if (!root.getOrDefault(flag(name), false)) { throw new AssertionError("Unpaid installation"); }
        LeggingsWitness.addInstallation(root, name);
    }

    public static List<String> credits(ItemStack root) {
        var credits = root.get(LeggingsWitness.component());
        return credits == null ? List.of() : credits.names();
    }

    static ItemStack snapshot(ItemStack root) {
        var copy = root.copy();
        for (String name : List.of("ability_bindings", "ability_cooldowns")) {
            @SuppressWarnings("unchecked")
            var type = (DataComponentType<List<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(
                    Identifier.fromNamespaceAndPath("justdirethings", name));
            if (type != null && copy.has(type)) { copy.set(type, List.copyOf(copy.get(type))); }
        }
        return copy;
    }

    record Target(ServerPlayer player, int slot, ItemStack root) {
        static boolean supportedSlot(int slot) {
            return slot >= 0 && slot < 36 || slot == Inventory.SLOT_OFFHAND || slot == EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE);
        }

        boolean present() {
            return player.getInventory().getItem(slot) == root;
        }
    }
}
