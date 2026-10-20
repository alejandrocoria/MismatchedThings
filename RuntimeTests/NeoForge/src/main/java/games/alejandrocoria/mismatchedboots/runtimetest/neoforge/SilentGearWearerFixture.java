package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerApi;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Direct binding controls and a recalculation observer; no candidate set routing remains. */
public final class SilentGearWearerFixture {
    private static SilentGearWearerApi api;
    public static boolean active;
    private SilentGearWearerFixture() {}
    public static void bind() throws ReflectiveOperationException { api = new SilentGearWearerApi(); }
    public static ItemStack carrier(ItemStack root) throws Throwable { return api.carrier(root); }
    public static List<?> effects(Object instance) throws Throwable { return api.effects(instance); }
    public static boolean isWielder(Object effect) { return api.isWielder(effect); }
    public static void tick(Object effect, Player player, Object instance, ItemStack carrier, boolean selected) throws Throwable { api.tick(effect, player, instance, carrier, selected); }
}
