package games.alejandrocoria.mismatchedleggings.compatibility.iceandfire;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Independent early API admission and final local consumer proofs, after MixinExtras. */
public final class IceAndFireMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean damage;
    private boolean lightning;
    private boolean examined;

    @Override
    public void onLoad(String mixinPackage) {
        var provider = MixinService.getService().getBytecodeProvider();
        ClassNode handler;
        try {
            handler = provider.getClassNode(IceAndFireDamageShape.HANDLER.replace('/', '.'));
        } catch (ClassNotFoundException absent) {
            return;
        } catch (Exception | LinkageError unsupported) {
            decline(false);
            decline(true);
            return;
        }
        try {
            damage = IceAndFireDamageShape.isCompatible(handler,
                    provider.getClassNode(IceAndFireDamageShape.SCALE.replace('/', '.')),
                    provider.getClassNode(IceAndFireDamageShape.STEEL.replace('/', '.')),
                    provider.getClassNode(IceAndFireDamageShape.TROLL.replace('/', '.')));
        } catch (Exception | LinkageError unsupported) {
            damage = false;
        }
        try {
            lightning = IceAndFireDamageShape.isLightningCompatible(handler,
                    provider.getClassNode(IceAndFireDamageShape.ITEMS.replace('/', '.')));
        } catch (Exception | LinkageError unsupported) {
            lightning = false;
        }
        if (!damage) { decline(false); }
        if (!lightning) { decline(true); }
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) { return damage || lightning; }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!IceAndFireDamageShape.HANDLER.equals(node.name) || examined) { return; }
        examined = true;
        if (damage && !IceAndFireDamageShape.apply(node, false)) { decline(false); }
        if (lightning && !IceAndFireDamageShape.apply(node, true)) { decline(true); }
    }

    private static void decline(boolean lightning) {
        LeggingsContent.LOG.warn("Ice and Fire {} mixed leggings recognition disabled: incompatible local contract/API",
                lightning ? "lightning-set" : "projectile/dragon");
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
