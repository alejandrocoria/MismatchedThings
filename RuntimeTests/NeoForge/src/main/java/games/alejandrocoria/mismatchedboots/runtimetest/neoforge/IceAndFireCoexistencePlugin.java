package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Test-only pre-application mutations exercise late local guards, not just unchanged selectors. */
public final class IceAndFireCoexistencePlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return !System.getProperty("mismatchedboots.runtimetest.iceAndFireCoexistence", "").isEmpty();
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("IceAndFireCoexistenceHighMixin")) {
            return;
        }
        for (MethodNode method : node.methods) {
            if (!method.name.equals("onEntityDamage") && !method.name.equals("onLivingHurt")) {
                continue;
            }
            // Direct executable insertion: the former full-body hash rejected this harmless log-like call.
            method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/IceAndFireCoexistencePlugin",
                    "observe", "()V", false));
            if (System.getProperty("mismatchedboots.runtimetest.iceAndFireCoexistence").equals("incompatible")
                    && method.name.equals("onEntityDamage")) {
                // Replace only the FEET Troll type test with a non-armor type before injectors prepare.
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                            && type.desc.endsWith("/TrollArmorItem")
                            && type.getPrevious().getPrevious().getPrevious() instanceof FieldInsnNode slot
                            && slot.name.equals("FEET")) {
                        type.desc = "java/lang/String";
                    }
                }
            }
        }
    }

    public static void observe() {
        IceAndFireCoexistenceFixture.logCalls++;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
