package games.alejandrocoria.mismatchedleggings.test.mystical;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class SpikePlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String name) { return true; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() {
        if (!Boolean.getBoolean("leggings.test.mysticalSpike") || Boolean.getBoolean("leggings.test.maAbsent")) {
            return List.of();
        }
        return List.of("EquipmentCandidate", "ArmorTickCandidate", "HealthCandidate", "SpeedWitness",
                System.getProperty("leggings.test.maRefund", "observer").equals("router") ? "RefundRouter" : "RefundObserver");
    }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
