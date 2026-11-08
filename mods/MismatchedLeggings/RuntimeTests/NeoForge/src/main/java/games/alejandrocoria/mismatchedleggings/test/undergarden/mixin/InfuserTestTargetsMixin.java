package games.alejandrocoria.mismatchedleggings.test.undergarden.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"quek.undergarden.recipe.InfusingRecipe", "quek.undergarden.inventory.InfuserMenu"}, remap = false)
public interface InfuserTestTargetsMixin {}
