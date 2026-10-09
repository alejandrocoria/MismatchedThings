package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpCompatibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.events.LivingEntityEvents", remap = false)
abstract class JustDireJumpMixin {
    /** Published only after final transformed paired-hook verification; early selection is not permission. */
    @Unique
    private static boolean mismatchedboots$justDireJumpReady() { return false; }

    /** Separate final permission to debit FE; a denied Negate contract does not disable safe Jump. */
    @Unique
    private static boolean mismatchedboots$justDireNegateFallReady() { return false; }

    @WrapOperation(method = "jumpEvent(Lnet/neoforged/neoforge/event/entity/living/LivingEvent$LivingJumpEvent;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpFeetInjectionPoint"),
            require = 0, expect = 0, allow = 1)
    private static ItemStack mismatchedboots$justDireJumpFeet(Player player, EquipmentSlot slot, Operation<ItemStack> original) {
        ItemStack root = original.call(player, slot);
        if (mismatchedboots$justDireJumpReady()) { JustDireJumpCompatibility.jump(player, root); }
        return root;
    }

    @WrapOperation(method = "LivingFallDamage(Lnet/neoforged/neoforge/event/entity/living/LivingFallEvent;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpFeetInjectionPoint"),
            require = 0, expect = 0, allow = 1)
    private static ItemStack mismatchedboots$justDireFallFeet(ServerPlayer player, EquipmentSlot slot,
                                                            Operation<ItemStack> original, LivingFallEvent event) {
        ItemStack root = original.call(player, slot);
        if (mismatchedboots$justDireJumpReady()) {
            JustDireJumpCompatibility.fall(event, root, mismatchedboots$justDireNegateFallReady());
        }
        return root;
    }
}
