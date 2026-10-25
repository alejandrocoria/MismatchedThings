package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Actual competing edits, never a test implementation of mixed protection. */
public final class UndergardenTestPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String mode = System.getProperty("mismatchedboots.runtimetest.undergarden", "");
        return mode.equals("chains") || mode.equals("refused");
    }

    @Override
    public void preApply(String target, ClassNode owner, String mixin, IMixinInfo info) {
        boolean refused = System.getProperty("mismatchedboots.runtimetest.undergarden", "").equals("refused");
        for (var method : owner.methods) {
            if (!method.name.equals("entityInside")) {
                continue;
            }
            for (var n : method.instructions.toArray()) {
                if (n instanceof FieldInsnNode f && f.name.equals("CLOGGRUM_BOOTS") && refused) {
                    f.name = "FROSTSTEEL_BOOTS";
                }
                if (!refused && n instanceof MethodInsnNode c && (c.name.equals("hasEffect")
                        || c.name.equals("is") && c.desc.equals("(Lnet/minecraft/core/Holder;)Z"))) {
                    method.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                                     "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/UndergardenTest",
                                                                     "observe", "(Z)Z", false));
                }
            }
        }
    }

    @Override
    public void onLoad(String name) {
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
    public void postApply(String target, ClassNode owner, String mixin, IMixinInfo info) {
    }
}
