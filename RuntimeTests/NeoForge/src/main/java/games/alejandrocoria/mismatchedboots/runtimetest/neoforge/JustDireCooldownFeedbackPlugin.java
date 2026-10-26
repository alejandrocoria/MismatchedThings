package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Production observers and opt-in local HUD faults/wrappers, never replacement HUD or sound emission. */
public final class JustDireCooldownFeedbackPlugin implements IMixinConfigPlugin {
    private boolean enabled;
    private boolean lifecycle;

    @Override
    public void onLoad(String mixinPackage) {
        lifecycle = !System.getProperty("mismatchedboots.runtimetest.justDireGroundStompLifecycle", "").isEmpty();
        enabled = (lifecycle || Boolean.getBoolean("mismatchedboots.runtimetest.justDireCooldownHudTest"))
                && !JustDireCooldownFeedbackTestSupport.VARIANT.equals("absent");
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!enabled) {
            return false;
        }

        String variant = JustDireCooldownFeedbackTestSupport.VARIANT;
        if (mixin.endsWith("HudMissingMixin")) {
            return !lifecycle && variant.equals("hooksMissing");
        }
        if (mixin.endsWith("MetadataMissingMixin")) {
            return !lifecycle && variant.equals("parametersMissing");
        }
        if (mixin.endsWith("HighMixin")) {
            return !lifecycle && variant.equals("high");
        }
        if (mixin.endsWith("LowMixin")) {
            return !lifecycle && variant.equals("low");
        }

        return mixin.endsWith("DrawMixin") || mixin.endsWith("SoundMixin");
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("HudMissingMixin")) {
            return;
        }
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(
                        "games/alejandrocoria/mismatchedboots/client/compatibility/justdirethings/JustDireCooldownHud") && call.name.equals("view")) {
                    // Discard owned output while retaining its merged helper to test final reachable-use proof.
                    var discard = new InsnList();
                    discard.add(new InsnNode(Opcodes.POP));
                    discard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    method.instructions.insert(call, discard);
                }
            }
        }
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
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
}
