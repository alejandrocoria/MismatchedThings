package games.alejandrocoria.mismatchedleggings.mixin.compatibility.undergarden;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Trigger final-transformed local proofs without linking optional native classes. */
@Pseudo
@Mixin(targets = {"quek.undergarden.recipe.InfusingRecipe", "quek.undergarden.inventory.InfuserMenu"}, remap = false)
public interface InfuserTargetsMixin {}
