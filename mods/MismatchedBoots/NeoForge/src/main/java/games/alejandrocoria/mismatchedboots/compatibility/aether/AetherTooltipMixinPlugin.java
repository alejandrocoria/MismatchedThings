package games.alejandrocoria.mismatchedboots.compatibility.aether;

import net.neoforged.fml.loading.FMLEnvironment;
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

/** Client-only final local producer edits, independent from gameplay recognition. */
public final class AetherTooltipMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available;

    @Override
    public void onLoad(String name) {
        if (!FMLEnvironment.getDist().isClient()) {
            return;
        }

        try {
            MixinService.getService().getBytecodeProvider().getClassNode(AetherTooltipShape.EVENTS);
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(this);
                available = true;
            } else {
                AetherTooltipState.refuse(true, null);
                AetherTooltipState.refuse(false, null);
            }
        } catch (ClassNotFoundException absent) { /* Optional absence is quiet, before target parsing. */ } catch (Exception | LinkageError failure) {
            AetherTooltipState.refuse(true, failure);
            AetherTooltipState.refuse(false, failure);
        }
    }

    @Override
    public List<String> getMixins() {
        return available ? List.of("AetherTooltipEventsTargetMixin", "AetherTooltipFormatterTargetMixin") : List.of();
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return available;
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(AetherTooltipShape.FORMATTER)) {
            try {
                if (AetherTooltipShape.sourceScope(node)) {
                    AetherTooltipState.scopeReady();
                } else {
                    AetherTooltipState.refuse(true, null);
                }
            } catch (Exception | LinkageError failure) {
                AetherTooltipState.refuse(true, failure);
            }
        } else if (node.name.equals(AetherTooltipShape.EVENTS)) {
            for (boolean attributes : new boolean[]{true, false}) {
                try {
                    if (AetherTooltipShape.producer(node, attributes)) {
                        AetherTooltipState.ready(attributes);
                    } else {
                        AetherTooltipState.refuse(attributes, null);
                    }
                } catch (Exception | LinkageError failure) {
                    AetherTooltipState.refuse(attributes, failure);
                }
            }
        }
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return available;
    }

    @Override
    public void preApply(ITargetClassContext context) {
    }

    @Override
    public void postApply(ITargetClassContext context) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
