package games.alejandrocoria.mismatchedleggings.compatibility.oritech;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shape checks only: unrelated method-body transformations do not invalidate the bridge. */
public final class OritechMixinPlugin implements IMixinConfigPlugin {
    private static final String ARMOR = "com.geckolib.renderer.GeoArmorRenderer";
    private static final String PROVIDER = "com.geckolib.animatable.client.GeoRenderProvider";
    private static final String BONES = "com.geckolib.animation.state.BoneSnapshot";
    private Boolean compatible;

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (compatible != null) {
            return compatible;
        }
        try {
            var bytes = MixinService.getService().getBytecodeProvider();
            bytes.getClassNode("rearth.oritech.item.tools.armor.ExoArmorItem");
            bytes.getClassNode("rearth.oritech.client.renderers.ExosuitArmorRenderer");
            compatible = hasApi(Map.of(ARMOR, bytes.getClassNode(ARMOR), PROVIDER, bytes.getClassNode(PROVIDER),
                    BONES, bytes.getClassNode(BONES)));
            if (!compatible) {
                LeggingsContent.LOG.warn("Oritech Exo rendering API is incompatible; compatibility disabled");
            }
        } catch (ClassNotFoundException absent) {
            compatible = false;
        } catch (Exception | LinkageError failure) {
            compatible = false;
            LeggingsContent.LOG.warn("Cannot inspect Oritech rendering API; compatibility disabled", failure);
        }
        return compatible;
    }

    public static boolean hasApi(Map<String, ClassNode> types) {
        return method(types.get(ARMOR), "getSegmentsForSlot",
                "(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Ljava/util/List;", false)
                && method(types.get(ARMOR), "getBoneNameForSegment",
                "(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lcom/geckolib/renderer/GeoArmorRenderer$ArmorSegment;)Ljava/lang/String;", false)
                && method(types.get(PROVIDER), "of", "(Lnet/minecraft/world/item/ItemStack;)Lcom/geckolib/animatable/client/GeoRenderProvider;", true)
                && method(types.get(PROVIDER), "getGeoArmorRenderer",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/geckolib/renderer/GeoArmorRenderer;", false)
                && method(types.get(BONES), "skipRender", "(Z)Lcom/geckolib/animation/state/BoneSnapshot;", false)
                && method(types.get(BONES), "skipChildrenRender", "(Z)Lcom/geckolib/animation/state/BoneSnapshot;", false);
    }

    private static boolean method(ClassNode type, String name, String descriptor, boolean isStatic) {
        return type != null && (type.access & Opcodes.ACC_PUBLIC) != 0
                && type.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                && (method.access & Opcodes.ACC_PUBLIC) != 0 && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic);
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public void preApply(String name, ClassNode type, String mixin, IMixinInfo info) { }
    @Override public void postApply(String name, ClassNode type, String mixin, IMixinInfo info) { }
}
