package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireCooldownFeedbackSpike;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {"games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility",
        "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility"}, remap = false)
abstract class JustDireCooldownTickMixin {
    @WrapMethod(method = "tickEquipped")
    private static void fixtureTick(ServerPlayer player, Operation<Void> original) {
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        var before = JustDireCooldownFeedbackSpike.before(player);
        original.call(player);
        JustDireCooldownFeedbackSpike.completed(player, root, before);
    }
}
