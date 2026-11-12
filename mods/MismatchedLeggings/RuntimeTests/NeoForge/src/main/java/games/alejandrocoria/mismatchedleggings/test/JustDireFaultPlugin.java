package games.alejandrocoria.mismatchedleggings.test;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Corrupt only this product's unpublished permission seam; native callbacks remain healthy controls. */
public final class JustDireFaultPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("JustDirePresentationInputProbe")) {
            return Boolean.getBoolean("leggings.test.jdtPresentation");
        }
        String fault = System.getProperty("leggings.test.jdtFault", "");
        return fault.equals("movement") && target.endsWith("AbilityMethods")
                || fault.equals("attributes") && target.endsWith("CommonHooks")
                || fault.equals("screen") && target.endsWith("ToolSettingScreen");
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("JustDirePresentationInputProbe")) { return; }
        String getter = target.endsWith("AbilityMethods") ? "mismatchedleggings$jdtMovementReady"
                : target.endsWith("ToolSettingScreen") ? "mismatchedleggings$jdtSettingsReady" : "mismatchedleggings$justDireAttributesReady";
        var method = node.methods.stream().filter(value -> value.name.equals(getter)).findFirst().orElseThrow();
        method.instructions.set(method.instructions.getFirst(), new InsnNode(Opcodes.ICONST_1));
    }

    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
