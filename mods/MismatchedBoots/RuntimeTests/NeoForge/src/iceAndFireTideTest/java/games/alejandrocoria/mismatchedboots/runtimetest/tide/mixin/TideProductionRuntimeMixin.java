package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.tide.TideProductionProbe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Instruments only our adapter. Never adds work to the audited native entry or Item base tick. */
@Mixin(value = IceAndFireTideCompatibility.class, remap = false)
abstract class TideProductionRuntimeMixin {
    private static ItemStack previousCarrier;

    @WrapOperation(method = "activate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void productionTick(Item receiver, ItemStack carrier, ServerLevel level, Entity entity,
                                       EquipmentSlot slot, Operation<Void> original) {
        Item fixed = BuiltInRegistries.ITEM.get(Identifier.parse("iceandfire:tide_blue_boots")).orElseThrow().value();
        if (receiver != fixed || carrier.getItem() != fixed || carrier.getCount() != 1 || carrier == previousCarrier) {
            throw new AssertionError("Production bridge did not use fixed blue receiver/fresh carrier");
        }
        previousCarrier = carrier;
        ItemStack before = carrier.copy();
        original.call(receiver, carrier, level, entity, slot);
        if (!ItemStack.matches(before, carrier)) {
            throw new AssertionError("Production carrier mutated");
        }
        TideProductionProbe.activations++;
    }

    @WrapMethod(method = "resolveReceiver")
    private static Item resolve(Operation<Item> original) {
        TideProductionProbe.bindings++;
        String fault = System.getProperty("mismatchedboots.runtimetest.tideFeetReceiver", "");
        if (fault.equals("linkage")) {
            throw new NoSuchMethodError("Injected production receiver preflight failure");
        }
        return fault.equals("replaced") ? Items.IRON_BOOTS : original.call();
    }

    @WrapMethod(method = "disable")
    private static void disable(String reason, Operation<Void> original) {
        TideProductionProbe.warnings++;
        original.call(reason);
    }
}
