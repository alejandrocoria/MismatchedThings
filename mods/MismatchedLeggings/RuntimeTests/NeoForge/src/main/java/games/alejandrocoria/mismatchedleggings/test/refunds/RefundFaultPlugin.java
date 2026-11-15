package games.alejandrocoria.mismatchedleggings.test.refunds;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
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
        if (name.endsWith("MaMarkerFaultMixin")) {
            return fault.equals("maMarker");
        }
        if (name.endsWith("ConsumptionMutationsMixin")) {
            return fault.equals("receipts");
        }
        if (name.endsWith("BindingRefusalMixin")) {
            return fault.equals("binding");
        }
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
        if (name.endsWith("MaMarkerFaultMixin")) {
            var marker = node.methods.stream().filter(method -> method.name.equals("mismatchedthings$maRefundReady")).findFirst().orElseThrow();
            var first = marker.instructions.getFirst();
            while (first.getOpcode() < 0) {
                first = first.getNext();
            }
            if (first.getOpcode() != Opcodes.ICONST_0) {
                throw new IllegalStateException("Expected unpublished production MA marker");
            }
            marker.instructions.set(first, new InsnNode(Opcodes.ICONST_1));
        }
    }

}
