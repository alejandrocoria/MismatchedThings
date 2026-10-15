package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Real competing transformations, isolated by JVM. No production permission/fault switches. */
public final class JustDireArmorWearBaselinePlugin implements IMixinConfigPlugin {
    private static final String PROBE = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/JustDireArmorWearFaults";
    private static String variant() { return System.getProperty("mismatchedboots.runtimetest.justDireArmorWearVariant", ""); }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String variant = variant();
        if (mixin.endsWith("JustDireArmorWearBeforeMixin")) { return variant.equals("wrapperBefore"); }
        if (mixin.endsWith("JustDireArmorWearAfterMixin")) { return variant.equals("wrapperAfter"); }
        if (mixin.endsWith("JustDireArmorWearDebitMixin")) { return variant.equals("transactions"); }
        if (mixin.endsWith("JustDireArmorWearCommitMixin")) { return variant.equals("transactions"); }
        if (mixin.endsWith("JustDireArmorWearQueryMixin")) { return variant.equals("queryFailure"); }
        if (mixin.endsWith("JustDireArmorWearBaselineMixin")) { return variant.equals("missing") || variant.startsWith("wrapper"); }
        return List.of("amount", "entry", "slot", "entity", "cancellation", "association", "router", "publication").contains(variant);
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!variant().equals("missing")) { return; }
        int changed = 0;
        for (var method : node.methods) {
            if (!method.name.equals("lambda$onArmorHurt$0")) { continue; }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("hurtAndBreak")) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/NeoForgeJustDireArmorWearTest";
                    call.name = "ordinary";
                    call.desc = "(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V";
                    changed++;
                }
            }
        }
        if (changed != 1) { throw new IllegalStateException("Missing-consumer fault did not remove the real target"); }
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = variant();
        if (mixin.endsWith("JustDireArmorWearBaselineMixin") && variant.startsWith("wrapper")) {
            var consumer = method(node, "lambda$onArmorHurt$0");
            consumer.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "surrounding", "()V", false));
            // An unrelated foreign method is deliberately not part of the wear proof.
            var unrelated = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "foreignUnrelated", "()V", null, null);
            unrelated.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(unrelated);
            return;
        }
        if (!mixin.endsWith("JustDireArmorWearLateMixin")) { return; }
        var consumer = method(node, "lambda$onArmorHurt$0");
        int changed = 0;
        if (variant.equals("publication")) {
            var getter = method(node, "mismatchedboots$justDireArmorWearReady");
            getter.instructions.set(getter.instructions.getFirst(), new InsnNode(Opcodes.ICONST_1)); changed++;
        } else if (variant.equals("router")) {
            for (var method : node.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("route")
                            && call.owner.endsWith("/JustDireArmorWearCompatibility")) {
                        // Keep executable ordinary fallback, but replace the owned permission argument.
                        for (var candidate : method.instructions) {
                            if (candidate instanceof MethodInsnNode getter && getter.name.equals("mismatchedboots$justDireArmorWearReady")) {
                                method.instructions.set(getter, new InsnNode(Opcodes.ICONST_1)); changed++; break;
                            }
                        }
                    }
                }
            }
        } else if (variant.equals("cancellation")) {
            var caller = method(node, "onArmorHurt");
            for (var instruction : caller.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("isCanceled")) {
                    // Evaluate the real check but force its result false; ordinary uncancelled hits still work.
                    var force = new org.objectweb.asm.tree.InsnList();
                    force.add(new InsnNode(Opcodes.POP)); force.add(new InsnNode(Opcodes.ICONST_0));
                    caller.instructions.insert(call, force); changed++;
                }
            }
        } else if (variant.equals("association")) {
            // The event's map now dispatches a different consumer, not just a similar synthetic name.
            var alternate = new MethodNode(consumer.access, "foreignArmorConsumer", consumer.desc, null, null);
            consumer.accept(alternate); alternate.name = "foreignArmorConsumer"; node.methods.add(alternate);
            for (var instruction : method(node, "onArmorHurt").instructions) {
                if (!(instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy)) { continue; }
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (indy.bsmArgs[i] instanceof org.objectweb.asm.Handle handle && handle.getName().equals(consumer.name)) {
                        indy.bsmArgs[i] = new org.objectweb.asm.Handle(handle.getTag(), handle.getOwner(), alternate.name, handle.getDesc(), false); changed++;
                    }
                }
            }
        } else {
            for (var instruction : consumer.instructions) {
                if (variant.equals("amount") && instruction instanceof FieldInsnNode field && field.name.equals("newDamage")) {
                    field.name = "originalDamage"; changed++;
                } else if (variant.equals("entry") && instruction instanceof FieldInsnNode field && field.name.equals("armorItemStack")) {
                    consumer.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "entry",
                            "(Lnet/neoforged/neoforge/event/entity/living/ArmorHurtEvent$ArmorEntry;)Lnet/minecraft/world/item/ItemStack;", false)); changed++;
                } else if (variant.equals("slot") && instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 1 && changed == 0) {
                    consumer.instructions.set(load, new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "CHEST", "Lnet/minecraft/world/entity/EquipmentSlot;")); changed++;
                } else if (variant.equals("entity") && instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0 && changed == 0) {
                    consumer.instructions.insert(load, new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "entity",
                            "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/entity/LivingEntity;", false)); changed++;
                }
            }
        }
        if (changed != 1) { throw new IllegalStateException("Wear fault " + variant + " changed " + changed + " sites"); }
    }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
}
