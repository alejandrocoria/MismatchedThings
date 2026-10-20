package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerApi;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerReadiness;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerShape;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

/** Test-only activation; no test switch or registration is shipped in the production adapter. */
public final class SilentGearWearerFixture {
    private static SilentGearWearerApi api;
    public static boolean active;
    private SilentGearWearerFixture() {}
    public static void bind() throws ReflectiveOperationException { api = new SilentGearWearerApi(); }
    public static ItemStack carrier(ItemStack root) throws Throwable { return api.carrier(root); }
    public static List<?> effects(Object instance) throws Throwable { return api.effects(instance); }
    public static boolean isWielder(Object effect) { return api.isWielder(effect); }
    public static void tick(Object effect, Player player, Object instance, ItemStack carrier, boolean selected) throws Throwable { api.tick(effect, player, instance, carrier, selected); }
    public static boolean predicate(SilentGearWearerReadiness proof, boolean original, Object ctx, EquipmentSlot slot, ItemStack stack) throws Throwable {
        return original || active && proof.mismatchedboots$sgSetReady() && slot == EquipmentSlot.FEET && api.recognizes(stack, ctx);
    }
    public static void publish(ClassNode node) {
        if (node.name.equals("net/silentchaos512/gear/gear/trait/effect/WielderEffectTraitEffect")) {
            SilentGearWearerShape.publish(node);
        }
    }
}
