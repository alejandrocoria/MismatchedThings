package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Test-only real debit/world-feedback probes. No production flags, callbacks or alternate handler. */
public final class JustDireEarthquakeServerGuardPlugin implements IMixinConfigPlugin {
    public static int fault, debits, failAt, heavySounds, particles;
    public static ServerPlayer player;

    public static void changeToggle() {
        JustDireAttributeTestSupport.set(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), "earthquake_toggle", false);
    }

    public static final class PostCommitFailure extends RuntimeException {
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("mismatchedboots.runtimetest.justDireEarthquakeServerTest")
                && System.getProperty("mismatchedboots.runtimetest.justDireEarthquakeStateVariant", "live").equals("live");
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
