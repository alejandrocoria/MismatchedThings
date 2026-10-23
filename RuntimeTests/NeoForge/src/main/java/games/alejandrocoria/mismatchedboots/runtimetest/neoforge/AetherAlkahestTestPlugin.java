package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Real competing transformations, completed before production's final local proof. */
public final class AetherAlkahestTestPlugin implements IMixinConfigPlugin, Opcodes {
    private static final String CONTROL = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/AetherAlkahestTestControl";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.aetherAlkahest", "");

    @Override public List<String> getMixins() {
        if (MODE.isEmpty() || MODE.equals("live") || MODE.equals("absent")) { return List.of(); }
        return MODE.endsWith("binding") ? List.of("AetherAlkahestBindingTestMixin")
                : List.of("AetherAlkahestFluidTestMixin", "AetherAlkahestArmorTestMixin");
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        boolean armor = target.endsWith("AetherIIEventListeners");
        if (MODE.endsWith("binding") || !MODE.equals("chains") && !MODE.startsWith(armor ? "armor" : "fluid")) { return; }
        var method = node.methods.stream().filter(m -> m.name.equals(armor ? "onArmorDamaged" : "entityInside")).findFirst().orElseThrow();
        if (MODE.equals("chains")) {
            for (String suffix : new String[]{"First", "Second"}) {
                peer(node, "stack" + suffix, "L" + STACK + ";", false);
                peer(node, "supplier" + suffix, "Ljava/util/function/Supplier;", false);
                peer(node, "result" + suffix, "Z", true);
            }
        }
        boolean changed = false;
        for (var n : method.instructions.toArray()) {
            if (!(n instanceof MethodInsnNode query) || !query.owner.equals(STACK) || !query.name.equals("has") || !query.desc.equals("(Ljava/util/function/Supplier;)Z")) { continue; }
            if (MODE.equals("chains")) {
                var supplier = query.getPrevious();
                for (String suffix : new String[]{"First", "Second"}) {
                    method.instructions.insertBefore(supplier, new MethodInsnNode(INVOKESTATIC, node.name, "mismatchedboots$test$stack" + suffix, "(L" + STACK + ";)L" + STACK + ";", false));
                    method.instructions.insertBefore(query, new MethodInsnNode(INVOKESTATIC, node.name, "mismatchedboots$test$supplier" + suffix, "(Ljava/util/function/Supplier;)Ljava/util/function/Supplier;", false));
                    method.instructions.insert(query, new MethodInsnNode(INVOKESTATIC, node.name, "mismatchedboots$test$result" + suffix, "(Z)Z", false));
                }
            } else {
                boolean receiver = MODE.endsWith("receiver");
                method.instructions.insertBefore(receiver ? query.getPrevious() : query, new MethodInsnNode(INVOKESTATIC, CONTROL,
                        receiver ? "opaqueStack" : "opaqueSupplier", receiver ? "(L" + STACK + ";)L" + STACK + ";"
                                : "(Ljava/util/function/Supplier;)Ljava/util/function/Supplier;", false));
            }
            changed = true; if (!MODE.equals("chains")) { break; }
        }
        if (!changed) { throw new AssertionError("Missing Alkahest competing control " + MODE); }
    }
    private static void peer(ClassNode node, String name, String type, boolean result) {
        var method = new MethodNode(ACC_PRIVATE | ACC_STATIC, "mismatchedboots$test$" + name, "(" + type + ")" + type, null, null);
        if (!result) { method.instructions.add(new MethodInsnNode(INVOKESTATIC, CONTROL, "observe", "()V", false)); }
        method.instructions.add(new VarInsnNode(result ? ILOAD : ALOAD, 0));
        if (result) { method.instructions.add(new MethodInsnNode(INVOKESTATIC, CONTROL, "result", "(Z)Z", false)); }
        method.instructions.add(new InsnNode(result ? IRETURN : ARETURN));
        method.maxLocals = 1; method.maxStack = 1;
        node.methods.add(method);
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
