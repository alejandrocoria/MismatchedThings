package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exercises the native combat-wear entry without fabricating a loaded network client. */
@Mixin(Player.class)
public interface ArmorWearInvoker {
    @Invoker("hurtArmor")
    void leggings$hurtArmor(DamageSource source, float amount);
}
