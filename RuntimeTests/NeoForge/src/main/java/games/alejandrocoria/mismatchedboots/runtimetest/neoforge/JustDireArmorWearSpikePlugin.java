package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Local flow guard and an opt-in real producer fault, not a foreign whole-method fingerprint. */
public final class JustDireArmorWearSpikePlugin implements IMixinConfigPlugin {
    private boolean ready;

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return !System.getProperty("mismatchedboots.runtimetest.justDireArmorWearSpike", "").isEmpty();
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        int valid = 0, faults = 0;
        for (var method : node.methods) {
            if (!method.name.equals("lambda$onArmorHurt$0") || !method.desc.equals(
                    "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/neoforged/neoforge/event/entity/living/ArmorHurtEvent$ArmorEntry;)V")) { continue; }
            for (var insn : method.instructions) {
                if (insn instanceof FieldInsnNode field && field.name.equals("newDamage")
                        && System.getProperty("mismatchedboots.runtimetest.justDireArmorWearSpike").equals("flowChanged")) {
                    field.name = "originalDamage"; faults++;
                }
                if (!(insn instanceof MethodInsnNode call) || !call.owner.equals("net/minecraft/world/item/ItemStack")
                        || !call.name.equals("hurtAndBreak") || !call.desc.equals(
                        "(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V")) { continue; }
                if (System.getProperty("mismatchedboots.runtimetest.justDireArmorWearSpike").equals("consumerChanged")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/JustDireArmorWearCandidate";
                    call.name = "ordinary";
                    call.desc = "(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V";
                    faults++; continue;
                }
                AbstractInsnNode p = previous(call);
                boolean matches = load(p, 1); p = previous(p);
                matches &= load(p, 0); p = previous(p);
                matches &= p != null && p.getOpcode() == Opcodes.F2I; p = previous(p);
                matches &= field(p, "newDamage", "F"); p = previous(p);
                matches &= load(p, 2); p = previous(p);
                matches &= field(p, "armorItemStack", "Lnet/minecraft/world/item/ItemStack;"); p = previous(p);
                matches &= load(p, 2);
                if (matches) { valid++; }
            }
        }
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireArmorWearSpike");
        if ((variant.equals("flowChanged") || variant.equals("consumerChanged")) && faults != 1) {
            throw new IllegalStateException("Armor wear producer fault did not reach its actual consumer");
        }
        ready = valid == 1;
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        long wrappers = node.methods.stream().flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(insn -> insn instanceof MethodInsnNode call && call.owner.equals(
                        "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/JustDireArmorWearCandidate") && call.name.equals("consume")).count();
        System.setProperty("mismatchedboots.runtimetest.armorWearConsumerReady", Boolean.toString(ready && wrappers == 1));
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node == null) { return null; }
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static boolean load(AbstractInsnNode node, int index) {
        return node instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == index;
    }
    private static boolean field(AbstractInsnNode node, String name, String descriptor) {
        return node instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && field.owner.equals("net/neoforged/neoforge/event/entity/living/ArmorHurtEvent$ArmorEntry")
                && field.name.equals(name) && field.desc.equals(descriptor);
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
