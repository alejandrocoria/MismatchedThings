package games.alejandrocoria.mismatchedleggings.test.refunds;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class RefundFaultPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String name) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String name) {
        String fault = System.getProperty("leggings.test.refundFault", "");
        return name.endsWith("ShapeFaultMixin") ? fault.equals("shape") : fault.equals("forward");
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String name, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String name, IMixinInfo info) {
    }
}
