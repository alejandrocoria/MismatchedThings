package games.alejandrocoria.mismatchedleggings.test.render;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Extra real-source pairs atop the maintained native decorated-waist/pose/reload assertions. */
public final class IceAndFireRenderingChecks {
    private IceAndFireRenderingChecks() {}

    public static void verify(HumanoidModel<HumanoidRenderState> original) {
        int variants = 0;
        int cases = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("iceandfire") || !id.getPath().endsWith("_leggings")) { continue; }
            variants++;
            for (boolean inverse : List.of(false, true)) {
                var source = new net.minecraft.world.item.ItemStack(item);
                var partner = RenderFixture.sources(2)[0]; // Native dyed/trimmed leather.
                source.set(DataComponents.TRIM, partner.get(DataComponents.TRIM));
                var root = RenderFixture.pair(inverse ? partner : source, inverse ? source : partner);
                root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                    var nativeFaces = ItemChecks.faces(ItemChecks.resolve(source, context));
                    var mixedFaces = ItemChecks.faces(ItemChecks.resolve(root, context));
                    require(!nativeFaces.isEmpty() && !mixedFaces.isEmpty()
                                    && nativeFaces.stream().allMatch(face -> mixedFaces.stream().anyMatch(mixed -> mixed.texture().equals(face.texture())))
                                    && mixedFaces.stream().allMatch(ItemChecks.Face::foil),
                            "Native item texture/live foil missing: " + id + " / " + context);
                }
                for (boolean crouch : List.of(false, true)) {
                    var state = new HumanoidRenderState();
                    state.isCrouching = crouch;
                    state.walkAnimationSpeed = .8F;
                    state.walkAnimationPos = 1.5F;
                    var output = new RecordingSubmits();
                    MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, original, state,
                            EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 37);
                    require(output.nodes.size() >= 4 && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                            "Native trimmed/glint pair fell back: " + id + " nodes=" + output.nodes.size());
                    require(output.nodes.stream().anyMatch(node -> node.sprite() != null), "Trim submission absent: " + id);
                    require(output.nodes.stream().anyMatch(node -> node.type().toString().toLowerCase(java.util.Locale.ROOT).contains("glint")),
                            "Live-root glint submission absent: " + id);
                    require(output.nodes.stream().anyMatch(node -> (node.color() & 0xFFFFFF) == 0x3366CC), "Dyed partner color absent: " + id);
                    for (var node : output.nodes) {
                        require(node.outline() == 37 && node.light() == 15728880, "Native material fields lost");
                    }
                    var nativeOutput = new RecordingSubmits();
                    for (var nativeSource : inverse ? List.of(partner, source) : List.of(source, partner)) {
                        var nativeView = nativeSource.copy();
                        nativeView.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                        EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                                nativeView.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), original, state,
                                nativeView, new PoseStack(), nativeOutput, 15728880, 37);
                    }
                    require(output.nodes.stream().map(IceAndFireRenderingChecks::material).toList()
                                    .equals(nativeOutput.nodes.stream().map(IceAndFireRenderingChecks::material).toList()),
                            "Exact ordered native materials changed: " + id);
                    cases++;
                }
            }
        }
        require(variants == 31 && cases == 124, "Incomplete native source render matrix");
        try {
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "ice-and-fire-render.txt"),
                    "31 actual sources; 124 both-orientation moving/crouched dyed-leather/trim/glint pairs; exact native materials, no fallback; GUI/first/third-person item textures/foil; repeated through maintained resource reload\n");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static List<Object> material(RecordingSubmits.Node<?> node) {
        return java.util.Arrays.asList(node.order(), node.type(), node.light(), node.overlay(), node.color(),
                node.sprite(), node.outline(), node.crumbling());
    }
}
