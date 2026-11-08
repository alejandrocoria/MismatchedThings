package games.alejandrocoria.mismatchedleggings.test.mysticaleffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
public final class GuardControl {
    private GuardControl() {}
    public static ItemStack opaqueSlot(Player player, EquipmentSlot slot) { return player.getItemBySlot(slot); }
    public static void opaqueAdd(Object cache, Object augment, Player player, Runnable cleanup) {
        NativeAugments.invoke(cache, "add", new Class<?>[]{NativeAugments.type("api.tinkering.Augment"), Player.class, Runnable.class}, augment, player, cleanup);
    }
}
