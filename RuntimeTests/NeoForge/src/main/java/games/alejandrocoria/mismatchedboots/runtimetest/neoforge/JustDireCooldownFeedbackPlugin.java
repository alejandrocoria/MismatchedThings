package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;
import java.util.Set;

/** Separate research transforms and production HUD observers/faults; no candidate emission in production runs. */
public final class JustDireCooldownFeedbackPlugin implements IMixinConfigPlugin, IExtension {
    private static final String HUD = "com/direwolf20/justdirethings/client/overlays/AbilityCooldownOverlay";
    private static final String CANDIDATE = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/JustDireCooldownFeedbackSpike";
    private boolean enabled;
    private boolean production;

    @Override public void onLoad(String mixinPackage) {
        production = Boolean.getBoolean("mismatchedboots.runtimetest.justDireCooldownHudTest");
        enabled = (production || Boolean.getBoolean("mismatchedboots.runtimetest.justDireCooldownFeedbackTest"))
                && !System.getProperty("mismatchedboots.runtimetest.justDireCooldownFeedbackVariant", "live").equals("absent");
        if (enabled && MixinEnvironment.getCurrentEnvironment().getActiveTransformer() instanceof IMixinTransformer transformer
                && transformer.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!enabled) { return false; }
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireCooldownFeedbackVariant", "live");
        if (mixin.endsWith("HudMissingMixin")) { return production ? variant.equals("hooksMissing") : true; }
        if (mixin.endsWith("MetadataMissingMixin")) { return production && variant.equals("parametersMissing"); }
        if (mixin.endsWith("HighMixin")) { return variant.equals("high"); }
        if (mixin.endsWith("LowMixin")) { return variant.equals("low"); }
        if (production) { return mixin.endsWith("DrawMixin") || mixin.endsWith("SoundMixin"); }
        return true;
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!node.name.equals(HUD)) { return; }
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireCooldownFeedbackVariant", "live");
        if (production && !mixin.endsWith("HudMissingMixin")) { return; }
        // Research faults its callback; production faults consumption while leaving the merged helper present.
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(CANDIDATE) && call.name.equals("view")
                        && !production && mixin.endsWith("HudMixin") && variant.equals("hooksMissing")) {
                    call.name = "unavailableView";
                } else if (mixin.endsWith("HudMissingMixin") && instruction instanceof MethodInsnNode call && call.owner.equals(
                        "games/alejandrocoria/mismatchedboots/client/compatibility/justdirethings/JustDireCooldownHud") && call.name.equals("view")) {
                    // Leave the merged bridge and render call in place, but discard its result.
                    var discard = new InsnList();
                    discard.add(new InsnNode(org.objectweb.asm.Opcodes.POP));
                    discard.add(new VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
                    method.instructions.insert(call, discard);
                }
            }
        }
    }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!node.name.equals(HUD) || production) { return; }
        int hooks = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(CANDIDATE) && call.name.equals("view")) { hooks++; }
            }
        }
        JustDireCooldownFeedbackSpike.hudReady = hooks == 1;
        if (hooks != 1) { System.out.println("JDT feedback spike: native HUD hook unavailable; mixed HUD only disabled"); }
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return enabled; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
