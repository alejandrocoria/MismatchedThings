package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Disposable failures of real mutation paths, not production fault switches. */
public final class JustDireGroundStompServerGuardPlugin implements IMixinConfigPlugin {
    public static int fault, extracted;
    public static ServerPlayer player;
    public static ItemStack replacement;
    public static Runnable debitMutation;
    public static void afterDebit() {
        if (debitMutation != null) { var mutation = debitMutation; debitMutation = null; mutation.run(); }
    }
    public static void changeToggle() { JustDireAttributeTestSupport.set(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), "groundstomp_toggle", false); }
    public static final class PostCommitFailure extends RuntimeException {}
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean server = Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompServerTest");
        boolean client = Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompClientTest");
        if (!server && !client) { return false; }
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompServerVariant", "live");
        if (mixin.endsWith("JustDireSettingsApiMissingMixin")) { return variant.equals("settingsMissing"); }
        return server && variant.equals("live");
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
