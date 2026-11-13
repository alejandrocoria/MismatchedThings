package games.alejandrocoria.mismatchedleggings.test;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** No product class is loaded for inspection; the legacy proof runs on a detached ASM clone. */
public final class WearSpikePlugin implements IMixinConfigPlugin, IExtension {
    public static volatile boolean commonReady;
    public static volatile boolean bridgeReady;
    private static final String BOOTS = "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireArmorWearCompatibility";
    private boolean available;
    private boolean bridge;

    @Override
    public void onLoad(String mixinPackage) {
        String variant = System.getProperty("leggings.test.wearVariant");
        bridge = !System.getProperty("leggings.test.wearBoots").equals("alone") && !variant.equals("naive");
        if (variant.equals("baseline")) {
            return;
        }
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            available = WearSpikeShape.entry(provider.getClassNode(WearSpikeShape.OWNER.replace('/', '.')));
            if (bridge) {
                var router = provider.getClassNode(BOOTS.replace('/', '.'));
                available &= router.methods.stream().anyMatch(method -> method.name.equals("route") && method.desc.equals(WearSpikeShape.ROUTE_DESC));
            }
            var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (available) {
                ((Extensions) transformer.getExtensions()).add(this);
            }
        } catch (Exception | LinkageError failure) {
            throw new IllegalStateException("Wear spike preflight failed", failure);
        }
    }

    @Override
    public List<String> getMixins() {
        if (!available) {
            return List.of();
        }
        var result = new ArrayList<String>();
        result.add(bridge ? "WearSpikeBridgeMixin" : "WearSpikeStandaloneMixin");
        result.add("WearSpikeDebitMixin");
        if (System.getProperty("leggings.test.wearTransform").equals("transparent")) {
            result.add("WearSpikeTransparentMixin");
        }
        return result;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return available;
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (System.getProperty("leggings.test.wearVariant").equals("refused")
                && (mixin.endsWith("WearSpikeBridgeMixin") || mixin.endsWith("WearSpikeStandaloneMixin"))) {
            var getter = node.methods.stream().filter(method -> method.name.equals(WearSpikeShape.GETTER)).findFirst().orElseThrow();
            getter.instructions.set(getter.instructions.getFirst(), new InsnNode(Opcodes.ICONST_1));
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        try {
            if (node.name.equals(WearSpikeShape.OWNER)) {
                commonReady = bridge ? bootsProof(node) : WearSpikeShape.publish(node);
                System.out.println("Wear spike incoming caller ready=" + commonReady);
            }
            if (bridge && node.name.equals(BOOTS)) {
                bridgeReady = WearSpikeShape.bridgePublish(node);
                System.out.println("Wear spike optional bridge ready=" + bridgeReady);
            }
        } catch (Exception | LinkageError failure) {
            throw new IllegalStateException("Wear spike final proof failed", failure);
        }
    }

    private static boolean bootsProof(ClassNode original) throws ReflectiveOperationException {
        var copy = new ClassNode();
        original.accept(copy);
        var getter = copy.methods.stream().filter(method -> method.name.equals("mismatchedboots$justDireArmorWearReady")).findFirst().orElse(null);
        if (getter == null) {
            return false;
        }
        // The installed verifier expects its unpublished false getter. Normalize only the clone,
        // regardless of extension ordering; the live Boots permission and router are never edited.
        for (var instruction : getter.instructions) {
            if (instruction.getOpcode() == Opcodes.ICONST_1) {
                getter.instructions.set(instruction, new InsnNode(Opcodes.ICONST_0));
            }
        }
        var verifier = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireArmorWearShape");
        var publish = verifier.getDeclaredMethod("publish", ClassNode.class);
        publish.setAccessible(true);
        return (boolean) publish.invoke(null, copy);
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public String getRefMapperConfig() { return null; }
}
