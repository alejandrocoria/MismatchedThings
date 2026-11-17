package games.alejandrocoria.mismatchedthings.internal.compatibility.neovitae;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Prove only the native marker-read slice, not unrelated rituals or surrounding hooks. */
public final class HellforgedMixinPlugin implements IMixinConfigPlugin {
    private boolean available;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            available = supports(MixinService.getService().getBytecodeProvider()
                    .getClassNode("com.breakinblocks.neovitae.common.item.soul.HellforgedHelper"));
            if (!available) {
                org.slf4j.LoggerFactory.getLogger("mismatchedthings/neovitae").warn("Unsupported native Hellforged read; inherited mixed recognition disabled");
            }
        } catch (ClassNotFoundException absent) {
            // Optional absence is normal on both products' independent graphs.
        } catch (Exception | LinkageError failure) {
            org.slf4j.LoggerFactory.getLogger("mismatchedthings/neovitae").warn("Could not inspect native Hellforged read", failure);
        }
    }

    public static boolean supports(ClassNode node) {
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("isHellforged")
                && candidate.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Z")
                && (candidate.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                .findFirst().orElse(null);
        if (method == null) {
            return false;
        }
        var code = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                code.add(instruction);
            }
        }
        int matches = 0;
        for (int i = 0; i + 5 < code.size(); i++) {
            if (code.get(i) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0
                    && code.get(i + 1) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals("com/breakinblocks/neovitae/common/datacomponent/NVDataComponents")
                    && field.name.equals("HELLFORGED") && code.get(i + 2).getOpcode() == Opcodes.ICONST_0
                    && code.get(i + 3) instanceof MethodInsnNode box && box.owner.equals("java/lang/Boolean")
                    && box.name.equals("valueOf") && box.desc.equals("(Z)Ljava/lang/Boolean;")
                    && code.get(i + 4) instanceof MethodInsnNode read && read.owner.equals("net/minecraft/world/item/ItemStack")
                    && read.name.equals("getOrDefault") && read.desc.equals("(Ljava/util/function/Supplier;Ljava/lang/Object;)Ljava/lang/Object;")
                    && code.get(i + 5).getOpcode() == Opcodes.CHECKCAST) {
                matches++;
            }
        }
        return matches == 1;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return available;
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
    }
}
