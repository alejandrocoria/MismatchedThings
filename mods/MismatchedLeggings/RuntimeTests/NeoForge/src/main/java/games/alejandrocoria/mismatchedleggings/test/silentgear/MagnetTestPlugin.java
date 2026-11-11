package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.NOP;

/** Edits precede final production export; the witness never grants magnet readiness. */
public final class MagnetTestPlugin implements IMixinConfigPlugin, IExtension {
    private static final String EFFECT = "net/silentchaos512/gear/gear/trait/effect/ItemMagnetTraitEffect";
    private static final String TRACKER = "net/silentchaos512/gear/gear/util/MagnetPullTracker";
    static String actualOrder = "";

    @Override
    public void onLoad(String name) {
        var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (System.getProperty("leggings.test.sgMagnetVariant", "").isEmpty()) {
            return false;
        }
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(target);
            return true;
        } catch (Exception | LinkageError absent) {
            return false;
        }
    }

    @Override
    public void preApply(ITargetClassContext context) {
        if (System.getProperty("leggings.test.sgMagnetVariant", "").isEmpty() || !context.getClassNode().name.equals(EFFECT)) {
            return;
        }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var names = new ArrayList<String>();
            int legs = -1;
            int boots = -1;
            int index = 0;
            for (var entry : (Iterable<?>) field.get(context)) {
                var info = (IMixinInfo) entry;
                String name = info.getClassName();
                names.add(name + "@" + info.getPriority());
                if (name.equals("games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear.SilentGearMagnetProofMixin")) {
                    legs = index;
                }
                if (name.equals("games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear.SilentGearMagnetProofMixin")) {
                    boots = index;
                }
                index++;
            }
            String order = System.getProperty("leggings.test.sgEffectsOrder", "default");
            if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || !order.equals("default") && (legs < boots) != order.equals("before"))) {
                throw new AssertionError("Unexpected actual magnet mixin order " + names);
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Override
    public void postApply(ITargetClassContext context) {
        var node = context.getClassNode();
        String variant = System.getProperty("leggings.test.sgMagnetVariant", "");
        if (variant.isEmpty() || !node.name.equals(EFFECT) && !node.name.equals(TRACKER)) {
            return;
        }
        if (variant.equals("proof") && node.name.equals(EFFECT)) {
            node.methods.removeIf(method -> method.name.equals("mismatchedleggings$sgMagnetReady"));
        }
        for (var method : node.methods) {
            if (variant.equals("compatible") && (method.name.equals("inventoryTick") || method.name.equals("pushItem"))) {
                method.instructions.insert(new InsnNode(NOP));
            }
            if (variant.equals("callback") && method.name.equals("inventoryTick")) {
                method.instructions.insert(new MethodInsnNode(INVOKESTATIC,
                        "games/alejandrocoria/mismatchedleggings/test/silentgear/MagnetChecks", "unknownNativeCall", "()V", false));
            }
        }
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
