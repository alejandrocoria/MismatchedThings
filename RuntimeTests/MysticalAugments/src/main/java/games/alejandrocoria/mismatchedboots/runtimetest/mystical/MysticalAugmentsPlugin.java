package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class MysticalAugmentsPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return !System.getProperty("mismatchedboots.test.mysticalAugments", "").isEmpty();
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
        // Do not register even a @Pseudo target in the absent run: Fabric logs its class lookup.
        String variant = System.getProperty("mismatchedboots.test.mysticalAugments", "");
        if (variant.startsWith("refund")) {
            var selected = new java.util.ArrayList<>(List.of("AugmentDropWitnessMixin", "MysticalRefundWitnessMixin", "MysticalShrinkControlMixin"));
            if (variant.equals("refundinner") || variant.equals("refundmissing") || variant.equals("refundextra")) {
                selected.add("MysticalRefundInnerMixin");
            }
            if (variant.equals("refundouter")) {
                selected.add("MysticalRefundOuterMixin");
            }
            return selected;
        }

        return switch (variant) {
            case "compatible" -> List.of("MysticalEquipmentPeerMixin", "MysticalJumpPeerMixin");
            case "noequipment" -> List.of("MysticalEquipmentPeerMixin");
            case "nojump" -> List.of("MysticalJumpPeerMixin");
            default -> List.of();
        };
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.test.mysticalAugments", "");
        for (var method : node.methods) {
            if (variant.equals("refundmissing") && method.name.equals("applyDamage")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("isBroken")) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/mystical/MysticalRefundControl";
                        call.name = "opaqueBroken";
                        call.desc = "(Lnet/minecraft/world/item/ItemStack;)Z";
                    }
                }
            } else if (variant.equals("refundextra") && method.name.equals("applyDamage")) {
                // A second harmless consumption call makes the local proof ambiguous.
                var extra = new InsnList();
                extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
                extra.add(new InsnNode(Opcodes.ICONST_0));
                extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "shrink", "(I)V", false));
                method.instructions.insert(extra);
            } else if (variant.equals("noequipment") && method.name.equals("getArmorAugments")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("getItemBySlot")) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/mystical/MysticalGuardControl";
                        call.name = "opaqueSlot";
                        call.desc = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
                    }
                }
            } else if (variant.equals("nojump") && method.name.equals("onArmorTick")) {
                var unsafe = new InsnList();
                unsafe.add(new VarInsnNode(Opcodes.ALOAD, 1));
                unsafe.add(new InsnNode(Opcodes.ICONST_1));
                unsafe.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "setDamageValue", "(I)V", false));
                method.instructions.insert(unsafe);
            } else {
                method.instructions.insert(new InsnNode(Opcodes.NOP));
            }
        }
    }
}
