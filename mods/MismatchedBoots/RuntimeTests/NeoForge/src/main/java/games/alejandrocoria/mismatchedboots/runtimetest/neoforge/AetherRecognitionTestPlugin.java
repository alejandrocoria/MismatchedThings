package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class AetherRecognitionTestPlugin implements IMixinConfigPlugin {
    private static final String CONTROL = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/AetherRecognitionTestControl";

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("mismatchedboots.runtimetest.aetherRecognitionVariant", "live");
        return Boolean.getBoolean("mismatchedboots.runtimetest.aetherIIRecognition") && !variant.equals("live");
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.runtimetest.aetherRecognitionVariant", "live");
        boolean sets = variant.equals("setchanged") && target.endsWith("EquipmentUtil");
        boolean sentry = variant.equals("sentrychanged") && target.endsWith("SentryArmor");
        if (!sets && !sentry) {
            return;
        }
        for (var method : node.methods) {
            if (!method.name.equals(sets ? "getArmorCount" : "playerFall")) {
                continue;
            }
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field && field.name.equals(sets ? "ARMOR_SET" : "SENTRY_BOOTS")) {
                    // The original operand/value is unchanged, but production cannot prove this opaque replacement's identity.
                    String type = sets ? "java/util/function/Supplier" : "net/minecraft/core/Holder";
                    method.instructions.insert(field, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                                         CONTROL,
                                                                         sets ? "opaqueSupplier" : "opaqueHolder",
                                                                         "(L" + type + ";)L" + type + ";",
                                                                         false));
                    return;
                }
            }
        }
        throw new AssertionError("Missing native Aether operand control " + variant);
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
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
