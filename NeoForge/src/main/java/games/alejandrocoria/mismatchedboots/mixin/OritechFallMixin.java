package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "rearth.oritech.item.tools.armor.ExoArmorItem", remap = false)
abstract class OritechFallMixin {
    @Unique
    private static final ResourceKey<Item> mismatchedboots$exoBoots = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath("oritech", "exo_boots"));

    @WrapOperation(method = "CancelFallDamage(Lnet/minecraft/world/damagesource/DamageSource;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At(value = "games.alejandrocoria.mismatchedboots.compatibility.oritech.OritechFallInjectionPoint"),
            require = 0, expect = 0, allow = 1)
    private static boolean mismatchedboots$recognizeExo(ItemStack stack, Holder<Item> item, Operation<Boolean> original) {
        // Only broaden this native FEET predicate: Oritech still filters damage, plays the servo and cancels the event.
        return original.call(stack, item) || item.is(mismatchedboots$exoBoots)
                && MixedBootsSources.anySource(stack, source -> source.is(item));
    }
}
