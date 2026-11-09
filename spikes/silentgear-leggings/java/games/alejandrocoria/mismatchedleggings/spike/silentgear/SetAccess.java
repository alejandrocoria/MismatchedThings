package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import net.minecraft.world.entity.player.Player;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.traits.TraitActionContext;

public interface SetAccess {
    boolean spike$ready();
    int spike$count(TraitActionContext context, GearType type, Player player);
}
