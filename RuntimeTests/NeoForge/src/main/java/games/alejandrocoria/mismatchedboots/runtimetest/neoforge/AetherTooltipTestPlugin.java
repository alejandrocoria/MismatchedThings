package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AetherTooltipTestPlugin implements IMixinConfigPlugin {
    private static final String CONTROL = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/AetherTooltipTestControl";
    private final String variant = System.getProperty("mismatchedboots.runtimetest.aetherTooltipVariant", "live");
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public List<String> getMixins() {
        if (!Boolean.getBoolean("mismatchedboots.runtimetest.aetherIITooltip")
                || Boolean.getBoolean("mismatchedboots.runtimetest.aetherTooltipAbsent")) { return List.of(); }
        var result = new ArrayList<>(List.of("AetherTooltipNativeProbeMixin", "AetherTooltipDetailsProbeMixin"));
        if (List.of("chains", "attributechanged", "tierchanged").contains(variant)) { result.add("AetherTooltipPeerMixin"); }
        if (variant.startsWith("charmfactory")) { result.add("AetherCharmFactoryFaultMixin"); }
        if (variant.equals("charmpopulatedfailure")) { result.add("AetherCharmSourceFaultMixin"); }
        return result;
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!target.endsWith("AetherIIEventListeners")) { return; }
        for (var method : node.methods) {
            if (!method.name.equals(variant.equals("tierchanged") ? "onAddTooltipsLowest" : "onAddAttributeTooltips")) { continue; }
            method.instructions.insert(new InsnNode(Opcodes.NOP));
            for (var n : method.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode call)) { continue; }
                if (variant.equals("chains") && call.name.equals("getStack")) {
                    method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, CONTROL, "identity",
                            "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", false));
                } else if (variant.equals("attributechanged") && call.name.equals("getStack")
                        || variant.equals("tierchanged") && call.name.equals("getContext")) {
                    String event = variant.equals("attributechanged") ? "net/neoforged/neoforge/event/AddAttributeTooltipsEvent"
                            : "net/neoforged/neoforge/event/entity/player/ItemTooltipEvent";
                    call.owner = CONTROL; call.name = variant.equals("attributechanged") ? "opaqueStack" : "opaqueContext";
                    call.desc = "(L" + event + ";)" + org.objectweb.asm.Type.getReturnType(call.desc).getDescriptor();
                    call.setOpcode(Opcodes.INVOKESTATIC); call.itf = false;
                }
            }
        }
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
