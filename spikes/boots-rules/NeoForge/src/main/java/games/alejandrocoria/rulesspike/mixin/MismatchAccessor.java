package games.alejandrocoria.rulesspike.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Read the actual native error screen, without replacing its message or presentation. */
@Mixin(ModMismatchDisconnectedScreen.class)
public interface MismatchAccessor {
    @Accessor("mismatchedChannelData")
    Map<Identifier, Component> rulesSpike$channels();
}
