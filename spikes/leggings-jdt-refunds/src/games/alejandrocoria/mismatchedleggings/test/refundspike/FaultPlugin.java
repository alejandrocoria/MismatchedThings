package games.alejandrocoria.mismatchedleggings.test.refundspike;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class FaultPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String name) {
        String fault = System.getProperty("refund.spike.fault", "");
        return name.endsWith("ShapeFaultMixin") ? fault.equals("shape") : fault.equals("forward");
    }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String name, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String name, IMixinInfo info) {}
}
