package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Only disposable competing transforms; no runtime fault switch in a production owner. */
public final class JustDireSettingsClientGuardPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompClientTest")) {
            return false;
        }
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompClientVariant", "live");
        return mixin.endsWith("HighMixin") && variant.equals("high") || mixin.endsWith("LowMixin") && variant.equals("low")
                || mixin.endsWith("MissingMixin") && variant.equals("hooksMissing");
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("MissingMixin")) {
            return;
        }

        var method = node.methods.stream()
                                 .filter(m -> m.name.equals("setSetting") && m.desc.equals("(Ljava/lang/String;I)V"))
                                 .findFirst()
                                 .orElseThrow();
        // Model a late competing replacement losing one mixed callback, but not screen metadata.
        method.instructions.clear();
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.maxStack = 0;
    }

    @Override
    public void onLoad(String mixinPackage) {
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
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
