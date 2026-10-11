package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.screens.ToolSettingScreen", priority = 500, remap = false)
abstract class JustDireSettingsClientMissingMixin {
    @Unique private boolean fixtureMissingAnchor;
}
