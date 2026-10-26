package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class RootsTickTestPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("mismatchedboots.runtimetest.rootsTicks", "");
        if (variant.isEmpty() || variant.equals("absent")) {
            return false;
        }
        if (mixin.endsWith("ReceiverFaultMixin")) {
            return variant.equals("receiver");
        }
        if (mixin.endsWith("HelperFaultMixin")) {
            return variant.equals("helper");
        }
        return true;
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

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.runtimetest.rootsTicks", "");
        boolean staff = variant.equals("staff") && target.endsWith(".StaffItem")
                || variant.equals("crystal") && target.endsWith(".CrystalStaffItem");
        boolean healing = variant.equals("healing") && target.endsWith(".ComponentSpellsEvent");
        if (!staff && !healing) {
            return;
        }
        for (var method : node.methods) {
            if (!method.name.equals(staff ? "releaseUsing" : "wildwoodArmorRegenFullset")) {
                continue;
            }
            if (java.util.Arrays.stream(method.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode call
                    && call.name.equals("foreignItem"))) {
                return;
            }

            boolean feet = false;
            int feetLocal = -1;
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field && field.name.equals("FEET")) {
                    feet = true;
                }
                if (healing && feet && instruction instanceof VarInsnNode local && local.getOpcode() == Opcodes.ASTORE) {
                    feetLocal = local.var;
                    feet = false;
                }
                if (healing && instruction instanceof VarInsnNode local && local.getOpcode() == Opcodes.ALOAD && local.var == feetLocal) {
                    feet = true;
                }
                if (feet && instruction instanceof MethodInsnNode call && call.name.equals("getItem")) {
                    method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                                        "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/RootsTickTestControl",
                                                                        "foreignItem",
                                                                        "(Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/item/Item;",
                                                                        false));
                    return;
                }
            }
            throw new AssertionError("Missing set fault insertion: " + variant);
        }
    }
}
