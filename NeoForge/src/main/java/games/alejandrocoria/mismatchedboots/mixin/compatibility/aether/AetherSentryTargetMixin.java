package games.alejandrocoria.mismatchedboots.mixin.compatibility.aether;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.equipment.armor.abilities.SentryArmor", remap = false)
interface AetherSentryTargetMixin {}
