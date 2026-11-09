package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class RefundPlugin implements IMixinConfigPlugin, Opcodes {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String name) { return true; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return Boolean.getBoolean("leggings.test.mysticalRefunds") ? List.of("RefundPathsMixin", "DeliveryMixin", "DropWitnessMixin") : List.of(); }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.maRefundVariant", "normal");
        if (!node.name.equals("net/minecraft/world/item/ItemStack")) { return; }
        for (var method : node.methods) {
            if (variant.equals("compatible")) { method.instructions.insert(new InsnNode(NOP)); }
            if (variant.equals("flow") && method.name.equals("applyDamage")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("isBroken")) {
                        call.setOpcode(INVOKESTATIC);
                        call.owner = "games/alejandrocoria/mismatchedleggings/test/mysticalrefunds/RefundControl";
                        call.name = "opaqueBroken"; call.desc = "(Lnet/minecraft/world/item/ItemStack;)Z";
                    }
                }
            }
        }
    }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
