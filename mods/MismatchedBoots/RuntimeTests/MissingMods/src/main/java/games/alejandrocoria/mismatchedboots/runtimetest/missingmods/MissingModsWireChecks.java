package games.alejandrocoria.mismatchedboots.runtimetest.missingmods;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Extends the existing separate-process TCP gate; integrated lifecycle clients do not prove wire serialization. */
public final class MissingModsWireChecks {
    private MissingModsWireChecks() {
    }

    public static void deliver(ServerPlayer player, ItemStack active) {
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var raw = (CompoundTag) ItemStack.CODEC.encodeStart(ops, active).getOrThrow();
        var patch = raw.getCompoundOrEmpty("components");
        patch.getCompoundOrEmpty("mismatchedboots:mixed_boots_data").getCompoundOrEmpty("left")
             .putString("id", "future:temporarily_missing_boots");
        var wallet = new CompoundTag();
        wallet.putLong("paid", 9_007_199_254_740_993L);
        wallet.putLongArray("opaque", new long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        patch.put("future:wallet", wallet);
        var root = ItemStack.CODEC.parse(ops, raw).getOrThrow();
        require(ModContent.suspendedBootsData(root).isPresent(), "TCP fixture did not use production suspension");
        player.setItemSlot(EquipmentSlot.FEET, root);
        player.getInventory().setItem(1, root.copy());
        NetworkAcceptanceFiles.write("server-recovery.txt", describe(root, player.registryAccess()));
    }

    public static boolean receive(ItemStack feet, ItemStack inventory, HolderLookup.Provider registries) {
        if (feet.isEmpty() || inventory.isEmpty()) {
            return false;
        }
        require(ItemStack.matches(feet, inventory), "TCP equipment and inventory differ");
        var archive = ModContent.suspendedBootsData(feet).orElseThrow();
        require(ModContent.getMixedBootsData(feet).isEmpty()
                        && !feet.get(DataComponents.EQUIPPABLE).damageOnHurt(), "TCP activated parked source data");
        require(archive.original().getCompoundOrEmpty("future:wallet").getLongOr("paid", 0) == 9_007_199_254_740_993L,
                "TCP lost protected paid-root precision");
        NetworkAcceptanceFiles.write("client-recovery.txt", describe(feet, registries));
        return true;
    }

    private static String describe(ItemStack root, HolderLookup.Provider registries) {
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), root).getOrThrow().toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
