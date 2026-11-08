package games.alejandrocoria.mismatchedleggings.test.undergarden;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Behavior-preserving peers: compatible boolean chain or an unproved local operand. */
public final class InfuserTestPlugin implements IMixinConfigPlugin, Opcodes {
    private static final String FAULTS = "games/alejandrocoria/mismatchedleggings/test/undergarden/InfuserFaults";

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("leggings.test.rogdoriumVariant", "normal");
        return Boolean.getBoolean("leggings.test.rogdoriumState") && !Boolean.getBoolean("leggings.test.rogdoriumAbsent")
                && (variant.equals("compatible") || variant.equals("recipe") && target.endsWith("InfusingRecipe")
                || variant.equals("menu") && target.endsWith("InfuserMenu"));
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.rogdoriumVariant");
        var method = node.methods.stream().filter(m -> m.name.equals(target.endsWith("InfusingRecipe") ? "matches" : "canInfuse")
                && !m.desc.contains("RecipeInput;")).findFirst().orElseThrow();
        var query = Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call && call.name.equals("test"))
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
        if (variant.equals("compatible")) {
            method.instructions.insert(new InsnNode(NOP));
            method.instructions.insert(query, new MethodInsnNode(INVOKESTATIC, FAULTS, "result", "(Z)Z", false));
        } else {
            // Runtime identity, unknown to the production proof; refuse only this local extension.
            method.instructions.insertBefore(query, new MethodInsnNode(INVOKESTATIC, FAULTS, "stack",
                    "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", false));
        }
    }

    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
