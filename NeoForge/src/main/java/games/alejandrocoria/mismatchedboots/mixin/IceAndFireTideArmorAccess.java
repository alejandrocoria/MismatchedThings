package games.alejandrocoria.mismatchedboots.mixin;

import net.minecraft.world.item.equipment.ArmorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false)
public interface IceAndFireTideArmorAccess {
    @Accessor("type")
    ArmorType mismatchedboots$tideArmorType();
}
