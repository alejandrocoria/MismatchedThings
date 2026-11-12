package games.alejandrocoria.jdtspike.mixin;

import com.direwolf20.justdirethings.client.screens.ToolSettingScreen;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ToolSettingScreen.class, remap = false)
public interface ScreenAccess {
    @Accessor("tool") ItemStack spikeTool();
    @Accessor("toolSlot") int spikeSlot();
    @Accessor("buttonToAbilityMap") Map<Button, Ability> spikeButtons();
    @Accessor("sliders") Map<Button, ExtendedSlider> spikeSliders();
    @Accessor("bindingEnabled") void spikeCapture(boolean value);
    @Accessor("bindingEnabled") boolean spikeCapture();
}
