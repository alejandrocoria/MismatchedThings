package spike.aether.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import spike.aether.Probe;

import java.util.function.Supplier;

/** Mechanism proof only: production still needs final local operand/provenance admission. */
@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.equipment.EquipmentUtil", remap = false)
abstract class SetOperandMixin {
    @WrapOperation(method = "getArmorCount", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private static Object aether42$operand(ItemStack root, Supplier<?> type, Operation<Object> original,
                                           LivingEntity wearer, TagKey<Item> requested) throws ReflectiveOperationException {
        Object nativeValue = original.call(root, type);
        var slot = root.getItem() instanceof MixedBootsItem ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        if (Probe.tag(nativeValue) == requested || requested == null || root != wearer.getItemBySlot(slot)) {
            return nativeValue;
        }
        for (var source : Probe.sources(root)) {
            if (Probe.tag(source.get(Probe.component("armor_set"))) == requested) {
                // Only this call's immutable operand changes, including the native null gate; nothing is equipped/written.
                return Class.forName("com.aetherteam.aetherii.item.components.ArmorSet").getConstructor(TagKey.class).newInstance(requested);
            }
        }
        return nativeValue;
    }
}
