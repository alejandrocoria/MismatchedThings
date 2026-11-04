package games.alejandrocoria.mismatchedboots.runtimetest.protocol.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModMismatchDisconnectedScreen.class)
public interface ProtocolMismatchAccessor {
    @Accessor("mismatchedChannelData")
    Map<Identifier, Component> mismatchedboots$channels();
}
