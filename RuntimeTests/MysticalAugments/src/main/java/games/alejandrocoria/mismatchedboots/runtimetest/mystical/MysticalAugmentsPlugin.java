package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class MysticalAugmentsPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return !System.getProperty("mismatchedboots.test.mysticalAugments", "").isEmpty();
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() {
        // Do not register even a @Pseudo target in the absent run: Fabric logs its class lookup.
        return switch (System.getProperty("mismatchedboots.test.mysticalAugments", "")) {
            case "live" -> List.of("AugmentEquipmentMixin");
            case "refund" -> List.of("AugmentRefundMixin", "AugmentDropWitnessMixin");
            default -> List.of();
        };
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
