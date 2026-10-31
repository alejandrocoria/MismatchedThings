package games.alejandrocoria.leggingsspike;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Optional;

/** Reflection keeps Boots entirely absent from standalone compile/runtime dependencies. */
public final class BootsCoexistence {
    public static boolean ironSeen;
    public static boolean goldSeen;

    private BootsCoexistence() {
    }

    @SuppressWarnings("unchecked")
    public static ItemStack mixed() {
        try {
            ItemStack iron = Fixture.source(Items.IRON_BOOTS, EquipmentAssets.IRON);
            iron.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setAsset(EquipmentAssets.IRON).build());
            ItemStack gold = Fixture.source(Items.GOLDEN_BOOTS, EquipmentAssets.GOLD);
            gold.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setAsset(EquipmentAssets.GOLD).build());
            Class<?> half = Class.forName("games.alejandrocoria.mismatchedboots.data.BootHalf");
            Object left = half.getConstructor(ItemStack.class).newInstance(iron);
            Object right = half.getConstructor(ItemStack.class).newInstance(gold);
            Class<?> dataClass = Class.forName("games.alejandrocoria.mismatchedboots.data.MixedBootsData");
            Object data = dataClass.getConstructor(half, half).newInstance(left, right);
            Class<?> content = Class.forName("games.alejandrocoria.mismatchedboots.content.ModContent");
            Optional<?> type = (Optional<?>) content.getMethod("mixedBootsDataType").invoke(null);
            var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("mismatchedboots", "mixed_boots"));
            return new ItemStack(Holder.direct(item), 1, DataComponentPatch.builder()
                    .set((DataComponentType<Object>) type.orElseThrow(), data)
                    .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setAsset(EquipmentAssets.IRON).build())
                    .build());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot construct unchanged Boots fixture", failure);
        }
    }
}
