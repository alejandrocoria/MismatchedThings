package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Isolated real binding/forwarding/final-flow faults; production has no test flags or permission switches. */
public final class JustDireRefundTestPlugin implements IMixinConfigPlugin {
    private static final String PROBE = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/JustDireRefundFaults";

    private static String variant() {
        return System.getProperty("mismatchedboots.runtimetest.justDireRefunds", "");
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String variant = variant();
        if (mixin.endsWith("JustDireRefundCallbackMixin")) {
            return !variant.isEmpty();
        }
        if (mixin.endsWith("JustDireRefundItemsMissingMixin")) {
            return variant.equals("itemsMissing");
        }
        if (mixin.endsWith("JustDireRefundMetadataMissingMixin")) {
            return variant.equals("metadataMissing");
        }
        if (mixin.endsWith("JustDireRefundInnerMixin")) {
            return variant.equals("inner");
        }
        if (mixin.endsWith("JustDireRefundOuterMixin")) {
            return variant.equals("outer");
        }
        if (mixin.endsWith("JustDireRefundEarlyMixin")) {
            return List.of("missing", "extra", "count", "entity").contains(variant);
        }
        return List.of("operation", "publication", "router").contains(variant);
    }

    @Override
    public void onLoad(String mixinPackage) {
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
        if (!mixin.endsWith("JustDireRefundEarlyMixin")) {
            return;
        }
        int changed = 0;
        for (var method : node.methods) {
            if (!method.name.equals("applyDamage") || !method.desc.contains("LivingEntity")) {
                continue;
            }
            if (variant().equals("entity")) {
                var identity = new org.objectweb.asm.tree.InsnList();
                identity.add(new VarInsnNode(Opcodes.ALOAD, 2));
                identity.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                PROBE,
                                                "entity",
                                                "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/entity/LivingEntity;",
                                                false));
                identity.add(new VarInsnNode(Opcodes.ASTORE, 2));
                method.instructions.insert(identity);
                changed++;
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name) && call.name.equals("shrink") && call.desc.equals(
                        "(I)V")) {
                    if (variant().equals("count")) {
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, "count", "(I)I", false));
                    } else if (variant().equals("extra")) {
                        var zero = new org.objectweb.asm.tree.InsnList();
                        zero.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        zero.add(new InsnNode(Opcodes.ICONST_0));
                        zero.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "shrink", "(I)V", false));
                        method.instructions.insertBefore(call, zero);
                    } else {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = PROBE;
                        call.name = "ordinary";
                        call.desc = "(Lnet/minecraft/world/item/ItemStack;I)V";
                    }
                    changed++;
                }
            }
        }
        if (changed != 1) {
            throw new IllegalStateException("Missing refund target did not change exactly one consumption");
        }
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("JustDireRefundLateMixin")) {
            finalFault(node);
        }
    }

    private static void finalFault(ClassNode node) {
        String variant = variant();
        int changed = 0;
        for (var method : node.methods) {
            if (variant.equals("publication") && method.name.equals("mismatchedboots$justDireRefundReady")) {
                method.instructions.set(method.instructions.getFirst(), new InsnNode(Opcodes.ICONST_1));
                changed++;
                continue;
            }
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) {
                    continue;
                }
                if (variant.equals("router") && call.name.equals("mismatchedboots$justDireRefundReady")) {
                    method.instructions.set(call, new InsnNode(Opcodes.ICONST_1));
                    changed++;
                } else if (variant.equals("operation") && call.owner.endsWith("/JustDireRefundCompatibility") && call.name.equals("route")) {
                    method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                                              PROBE,
                                                                              "operation",
                                                                              "(Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;",
                                                                              false));
                    changed++;
                }
            }
        }
        if (changed != 1) {
            throw new IllegalStateException("Refund final-flow fault " + variant + " changed " + changed + " sites");
        }
    }
}
