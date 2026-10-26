package games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly;

import com.zurrtum.create.AllItems;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.zurrtum.create.content.equipment.armor.DivingBootsItem", remap = false)
abstract class CreateFlyDivingBootsItemMixin {
    @Inject(
            method = "getWornItem(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void mismatchedboots$returnDivingSource(
            Entity entity, CallbackInfoReturnable<ItemStack> callback) {
        if (!callback.getReturnValue().isEmpty() || !(entity instanceof LivingEntity living)) {
            return;
        }

        ItemStack equipped = living.getItemBySlot(EquipmentSlot.FEET);
        if (equipped.isEmpty()
                || equipped.getCount() != 1
                || !(equipped.getItem() instanceof MixedBootsItem)) {
            return;
        }

        MixedBootsData data = ModContent.getMixedBootsData(equipped).orElse(null);
        if (data == null) {
            return;
        }

        ItemStack source = findSource(data, AllItems.NETHERITE_DIVING_BOOTS);
        if (source.isEmpty()) {
            source = findSource(data, AllItems.COPPER_DIVING_BOOTS);
        }
        if (!source.isEmpty()) {
            callback.setReturnValue(source);
        }
    }

    private static ItemStack findSource(MixedBootsData data, Item item) {
        ItemStack left = data.left().source();
        if (left.getItem() == item) {
            return left;
        }
        ItemStack right = data.right().source();
        return right.getItem() == item ? right : ItemStack.EMPTY;
    }
}
