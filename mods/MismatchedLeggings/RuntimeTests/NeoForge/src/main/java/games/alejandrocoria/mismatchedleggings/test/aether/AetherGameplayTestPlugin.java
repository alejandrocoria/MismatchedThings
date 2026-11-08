package games.alejandrocoria.mismatchedleggings.test.aether;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Native behavior stays identical; only the guarded operand provenance is unknown. */
public final class AetherGameplayTestPlugin implements IMixinConfigPlugin, Opcodes {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String mode = System.getProperty("leggings.test.aetherGameplay", "");
        return mode.equals("sets") && target.endsWith("EquipmentUtil") || mode.equals("fluid") && target.endsWith("AlkahestFluid")
                || mode.equals("armor") && target.endsWith("AetherIIEventListeners");
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String mode = System.getProperty("leggings.test.aetherGameplay");
        var method = node.methods.stream().filter(m -> m.name.equals(mode.equals("sets") ? "getArmorCount"
                : mode.equals("fluid") ? "entityInside" : "onArmorDamaged")).findFirst().orElseThrow();
        if (mode.equals("sets")) {
            for (var instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == IRETURN) {
                    // Numerically identical but unproved arithmetic must not receive a blind extra piece.
                    method.instructions.insertBefore(instruction, new InsnNode(ICONST_1));
                    method.instructions.insertBefore(instruction, new InsnNode(IMUL));
                }
            }
        } else {
            var query = java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("has"))
                    .findFirst().orElseThrow();
            int supplier = method.maxLocals++;
            method.instructions.insertBefore(query, new VarInsnNode(ASTORE, supplier));
            method.instructions.insertBefore(query, new MethodInsnNode(INVOKESTATIC,
                    "games/alejandrocoria/mismatchedleggings/test/aether/AetherFaultReceiver", "receiver",
                    "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", false));
            method.instructions.insertBefore(query, new VarInsnNode(ALOAD, supplier));
        }
        method.maxStack += 2;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
