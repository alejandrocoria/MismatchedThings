package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.NOP;

/** Records real sibling order and mutates only our owned route before final proof. Never grants readiness. */
public final class VoidWardTestPlugin implements IMixinConfigPlugin, IExtension {
    private static final String EVENTS = "net/silentchaos512/gear/event/GearEvents";
    static String actualOrder = "";
    static int mutations;

    private static boolean enabled() {
        return (!System.getProperty("leggings.test.sgVoidVariant", "").isEmpty() || Boolean.getBoolean("leggings.test.sgRemainingClient"))
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
        return enabled() ? List.of("VoidWardWitnessMixin") : List.of();
    }

    @Override
    public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals(EVENTS)) {
            return;
        }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var names = new ArrayList<String>();
            for (var entry : (Iterable<?>) field.get(context)) {
                names.add(((IMixinInfo) entry).getClassName());
            }
            int own = names.indexOf("games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear.SilentGearVoidWardMixin");
            int boots = names.indexOf("games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear.SilentGearVoidWardMixin");
            String order = System.getProperty("leggings.test.sgEffectsOrder", "default");
            if (own < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || !order.equals("default") && (own < boots) != order.equals("before"))) {
                throw new AssertionError("Unexpected actual Void Ward order: " + names);
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Override
    public void postApply(ITargetClassContext context) {
        var owner = context.getClassNode();
        if (!owner.name.equals(EVENTS)) {
            return;
        }
        String variant = System.getProperty("leggings.test.sgVoidVariant", "");
        for (var method : owner.methods) {
            if (variant.equals("compatible") && method.name.equals("onPlayerTick")) {
                method.instructions.insert(new InsnNode(NOP));
                mutations++;
            }
            if (!method.name.contains("mismatchedleggings$sgVoidWard")) {
                continue;
            }
            for (var instruction : method.instructions.toArray()) {
                if (variant.equals("permission") && instruction instanceof MethodInsnNode getter
                        && getter.name.equals("mismatchedleggings$sgVoidReady")) {
                    getter.name = "mismatchedleggings$sgIncomingReady";
                    mutations++;
                } else if (variant.equals("event") && instruction instanceof VarInsnNode argument
                        && argument.getOpcode() == ALOAD && argument.var == 1) {
                    method.instructions.set(instruction, new InsnNode(ACONST_NULL));
                    mutations++;
                }
            }
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return enabled(); }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
}
