package games.alejandrocoria.mismatchedleggings.test.awakenedset;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Only fixture code edits native operands; production performs the final local acceptance. */
public final class SetPlugin implements IMixinConfigPlugin, Opcodes {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) { return true; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() {
        return Boolean.getBoolean("leggings.test.awakenedSet") && !Boolean.getBoolean("leggings.test.awakenedAbsent")
                ? List.of("GrowthWitnessMixin") : List.of();
    }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        var method = node.methods.stream().filter(m -> m.name.equals("hasAwakenedSupremiumSet")).findFirst().orElseThrow();
        String variant = System.getProperty("leggings.test.awakenedVariant", "normal");
        String slot = variant.startsWith("boots-") ? "FEET" : variant.startsWith("leggings-") ? "LEGS" : "";
        boolean selected = false;
        for (var n : method.instructions.toArray()) {
            if (n instanceof FieldInsnNode f && f.owner.equals("net/minecraft/world/entity/EquipmentSlot")) { selected = f.name.equals(slot); }
            if (selected && n instanceof MethodInsnNode c && c.name.equals("is") && variant.endsWith("-opaque")) {
                c.setOpcode(INVOKESTATIC); c.owner = "games/alejandrocoria/mismatchedleggings/test/awakenedset/SetControl";
                c.name = "opaque"; c.desc = "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/Object;)Z";
                selected = false;
            }
        }
        if (variant.endsWith("-ambiguous")) {
            var code = java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() >= 0).toList();
            int start = -1;
            int end = -1;
            for (int i = 0; i < code.size(); i++) {
                if (code.get(i) instanceof FieldInsnNode field && field.owner.equals("net/minecraft/world/entity/EquipmentSlot") && field.name.equals(slot)) { start = i - 1; }
                if (start >= 0 && code.get(i) instanceof MethodInsnNode call && call.name.equals("is")) { end = i; break; }
            }
            if (start < 0 || end < 0) { throw new IllegalStateException("Missing fixture predicate"); }
            var store = (VarInsnNode) code.get(end + 1);
            var extra = new InsnList();
            for (int i = start; i <= end; i++) { extra.add(code.get(i).clone(new java.util.HashMap<>())); }
            for (int i = end + 2; i < code.size(); i++) {
                if (code.get(i) instanceof VarInsnNode load && load.getOpcode() == ILOAD && load.var == store.var) {
                    extra.add(new JumpInsnNode(IFEQ, ((JumpInsnNode) code.get(i + 1)).label));
                    method.instructions.insertBefore(load, extra); break;
                }
            }
        }
        if (variant.equals("compatible")) {
            method.instructions.insert(new InsnNode(NOP));
            var unrelated = new InsnList();
            unrelated.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;"));
            unrelated.add(new InsnNode(POP));
            unrelated.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "LEGS", "Lnet/minecraft/world/entity/EquipmentSlot;"));
            unrelated.add(new InsnNode(POP)); method.instructions.insert(unrelated);
            for (var n : method.instructions.toArray()) {
                if (n instanceof MethodInsnNode c && c.name.equals("is")) {
                    method.instructions.insert(c, new MethodInsnNode(INVOKESTATIC,
                            "games/alejandrocoria/mismatchedleggings/test/awakenedset/SetControl", "peer", "(Z)Z", false));
                    break;
                }
            }
        }
    }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
