package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.NOP;

/** Local proof tripwire and passive observers; never changes native cancellation or grants readiness. */
public final class CureTestPlugin implements IMixinConfigPlugin, IExtension {
    static int mutations;

    private static boolean enabled() {
        return !System.getProperty("leggings.test.sgCureVariant", "").isEmpty()
                && !Boolean.getBoolean("leggings.test.sgFoundationAbsent");
    }

    @Override
    public void onLoad(String name) {
        if (enabled() && MixinEnvironment.getCurrentEnvironment().getActiveTransformer() instanceof IMixinTransformer mixin
                && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public List<String> getMixins() {
        return enabled() ? List.of("CureRemovalWitnessMixin", "CureDispatchWitnessMixin") : List.of();
    }

    @Override
    public void postApply(ITargetClassContext context) {
        var owner = context.getClassNode();
        if (!owner.name.equals("net/silentchaos512/gear/gear/trait/effect/CancelEffectsTraitEffect")) {
            return;
        }
        String variant = System.getProperty("leggings.test.sgCureVariant", "");
        for (var method : owner.methods) {
            if (variant.equals("proof") && method.name.equals("mismatchedleggings$sgCureReady")) {
                // A changed placeholder cannot be promoted by production export; Boots' placeholder is untouched.
                method.instructions.set(method.instructions.getFirst(), new InsnNode(ICONST_1));
                mutations++;
            }
            if (variant.equals("compatible") && method.name.equals("inventoryTick")) {
                method.instructions.insert(new InsnNode(NOP));
                mutations++;
            }
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return enabled(); }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
}
