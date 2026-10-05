package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural runtime characterization of the shared standard equipment route.
 * The recording renderer deliberately does not invoke native texture submission.
 */
public final class NeoForgeEquipmentRenderingClientTest {
    private NeoForgeEquipmentRenderingClientTest() {
    }

    public static void assertStandardRoute() {
        HumanoidModel<HumanoidRenderState> model = model();
        HumanoidRenderState state = new HumanoidRenderState();
        RecordingEquipmentLayerRenderer renderer = new RecordingEquipmentLayerRenderer();
        ItemStack mixed = mixed(
                source(Items.IRON_BOOTS, EquipmentAssets.IRON),
                source(Items.GOLDEN_BOOTS, EquipmentAssets.GOLD));

        if (!MixedBootsEquipmentRenderer.renderLayers(
                renderer,
                EquipmentClientInfo.LayerType.HUMANOID,
                model,
                state,
                mixed,
                new PoseStack(),
                new SubmitNodeStorage(),
                0x00F000F0,
                0x11223344)) {
            throw new AssertionError("Standard NeoForge mixed equipment declined the native route");
        }
        assertCalls(renderer.calls(), model, Items.IRON_BOOTS, EquipmentAssets.IRON,
                Items.GOLDEN_BOOTS, EquipmentAssets.GOLD, false, null, null);
        assertCenterCrossingOwnership();

        ItemStack leather = source(Items.LEATHER_BOOTS, EquipmentAssets.LEATHER);
        leather.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x3366CC));
        ItemStack decorated = mixed(leather, source(Items.DIAMOND_BOOTS, EquipmentAssets.DIAMOND));
        decorated.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        renderer.reset();
        if (!MixedBootsEquipmentRenderer.renderLayers(
                renderer,
                EquipmentClientInfo.LayerType.HUMANOID,
                model,
                state,
                decorated,
                new PoseStack(),
                new SubmitNodeStorage(),
                0x00F000F0,
                0)) {
            throw new AssertionError("Decorated NeoForge mixed equipment declined the native route");
        }
        assertCalls(renderer.calls(), model, Items.LEATHER_BOOTS, EquipmentAssets.LEATHER,
                Items.DIAMOND_BOOTS, EquipmentAssets.DIAMOND, true,
                leather.get(DataComponents.DYED_COLOR), null);
        ItemStack storedLeft = ModContent.getMixedBootsData(decorated).orElseThrow().left().source();
        if (storedLeft.hasFoil()) {
            throw new AssertionError("Root foil mutated the stored NeoForge source snapshot");
        }

        ItemStack unsupported = source(Items.GOLDEN_BOOTS, null);
        renderer.reset();
        if (MixedBootsEquipmentRenderer.renderLayers(
                renderer,
                EquipmentClientInfo.LayerType.HUMANOID,
                model,
                state,
                mixed(source(Items.IRON_BOOTS, EquipmentAssets.IRON), unsupported),
                new PoseStack(),
                new SubmitNodeStorage(),
                0x00F000F0,
                0)) {
            throw new AssertionError("Unsupported NeoForge equipment partially entered the split route");
        }
        if (!renderer.calls().isEmpty()) {
            throw new AssertionError("Unsupported NeoForge equipment submitted a partial source before fallback");
        }
    }

    static HumanoidModel<HumanoidRenderState> model() {
        ModelPart root = LayerDefinition.create(mesh(), 64, 32).bakeRoot();
        return new HumanoidModel<>(root);
    }

    private static void assertCenterCrossingOwnership() {
        MeshDefinition mesh = mesh();
        PartDefinition leftLeg = mesh.getRoot().getChild("left_leg");
        leftLeg.addOrReplaceChild("center_crossing", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-6.0F, -2.0F, -2.0F, 12.0F, 4.0F, 4.0F), PartPose.ZERO);
        HumanoidModel<HumanoidRenderState> model = new HumanoidModel<>(
                LayerDefinition.create(mesh, 64, 32).bakeRoot());
        ModelPart crossing = model.leftLeg.getChild("center_crossing");
        RecordingEquipmentLayerRenderer renderer = new RecordingEquipmentLayerRenderer();
        if (!MixedBootsEquipmentRenderer.renderLayers(
                renderer,
                EquipmentClientInfo.LayerType.HUMANOID,
                model,
                new HumanoidRenderState(),
                mixed(source(Items.IRON_BOOTS, EquipmentAssets.IRON),
                        source(Items.GOLDEN_BOOTS, EquipmentAssets.GOLD)),
                new PoseStack(),
                new SubmitNodeStorage(),
                0x00F000F0,
                0)) {
            throw new AssertionError("Center-crossing fixture declined the standard route");
        }
        List<Call> calls = renderer.calls();
        if (!calls.getFirst().model().allParts().contains(crossing)
                || calls.get(1).model().allParts().contains(crossing)) {
            throw new AssertionError("Center-crossing geometry did not retain deterministic left-root ownership");
        }
    }

    private static MeshDefinition mesh() {
        return HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
    }

    static ItemStack mixed(ItemStack left, ItemStack right) {
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                .orElseThrow(() -> new AssertionError("Mismatched boots data component is not registered"));
        Item mixedItem = BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID);
        if (mixedItem == null) {
            throw new AssertionError("Mismatched boots item is not registered");
        }
        DataComponentPatch components = DataComponentPatch.builder()
                .set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID)
                .set(dataType, new MixedBootsData(new BootHalf(left), new BootHalf(right)))
                .build();
        return new ItemStack(Holder.direct(mixedItem), 1, components);
    }

    static ItemStack source(Item item, ResourceKey<EquipmentAsset> asset) {
        Equippable.Builder equippable = Equippable.builder(EquipmentSlot.FEET);
        if (asset != null) {
            equippable.setAsset(asset);
        }
        DataComponentPatch components = DataComponentPatch.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, equippable.build())
                .build();
        return new ItemStack(Holder.direct(item), 1, components);
    }

    private static void assertCalls(
            List<Call> calls,
            HumanoidModel<HumanoidRenderState> model,
            Item leftItem,
            ResourceKey<EquipmentAsset> leftAsset,
            Item rightItem,
            ResourceKey<EquipmentAsset> rightAsset,
            boolean foil,
            net.minecraft.world.item.component.DyedItemColor leftDye,
            ArmorTrim leftTrim) {
        if (calls.size() != 2) {
            throw new AssertionError("NeoForge standard route submitted " + calls.size() + " source calls instead of two");
        }
        assertCall(calls.getFirst(), "left", model.leftLeg, leftItem, leftAsset, foil, leftDye, leftTrim);
        assertCall(calls.get(1), "right", model.rightLeg, rightItem, rightAsset, foil, null, null);
    }

    private static void assertCall(
            Call call,
            String side,
            ModelPart root,
            Item item,
            ResourceKey<EquipmentAsset> asset,
            boolean foil,
            net.minecraft.world.item.component.DyedItemColor dye,
            ArmorTrim trim) {
        if (call.model().root() != root
                || call.item() != item
                || !call.asset().equals(asset)
                || call.foil() != foil
                || !java.util.Objects.equals(call.dye(), dye)
                || !java.util.Objects.equals(call.trim(), trim)) {
            throw new AssertionError("NeoForge standard equipment call did not preserve the expected "
                    + side + " source view: " + call);
        }
    }

    private static final class RecordingEquipmentLayerRenderer extends EquipmentLayerRenderer {
        private final List<Call> calls = new ArrayList<>();

        private RecordingEquipmentLayerRenderer() {
            super(null, null);
        }

        @Override
        public <S> void renderLayers(
                EquipmentClientInfo.LayerType layerType,
                ResourceKey<EquipmentAsset> asset,
                Model<? super S> model,
                S state,
                ItemStack stack,
                PoseStack poseStack,
                SubmitNodeCollector collector,
                int light,
                int outlineColor) {
            calls.add(new Call(asset, model, stack.getItem(), stack.hasFoil(),
                    stack.get(DataComponents.DYED_COLOR), stack.get(DataComponents.TRIM)));
        }

        private List<Call> calls() {
            return List.copyOf(calls);
        }

        private void reset() {
            calls.clear();
        }
    }

    private record Call(
            ResourceKey<EquipmentAsset> asset,
            Model<?> model,
            Item item,
            boolean foil,
            net.minecraft.world.item.component.DyedItemColor dye,
            ArmorTrim trim) {
    }
}
