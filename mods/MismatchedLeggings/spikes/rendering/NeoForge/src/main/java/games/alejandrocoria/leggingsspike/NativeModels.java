package games.alejandrocoria.leggingsspike;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Rotations;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.ModList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic native extensions test replacement and atomic rejection without external JARs. */
final class NativeModels {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("leggings_spike");
    private static final DeferredItem<Item> CUSTOM = ITEMS.registerSimpleItem("custom");
    private static final DeferredItem<Item> UNSUPPORTED = ITEMS.registerSimpleItem("unsupported");
    private static int resolutions;
    private static Model<HumanoidRenderState> replacement;

    private NativeModels() {
    }

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((RegisterClientExtensionsEvent event) -> {
            event.registerItem(new IClientItemExtensions() {
                @Override
                @SuppressWarnings("rawtypes")
                public Model getGenericArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layer, Model original) {
                    resolutions++;
                    return replacement;
                }
            }, CUSTOM, UNSUPPORTED);
            SpikeClient.extraChecks = NativeModels::verify;
        });
    }

    @SuppressWarnings("unchecked")
    private static String verify() {
        var models = Minecraft.getInstance().getEntityModels();
        HumanoidModel<HumanoidRenderState> original = new HumanoidModel<>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        HumanoidModel<HumanoidRenderState> animated = new HumanoidModel<>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs())) {
            @Override
            public void setupAnim(HumanoidRenderState state) {
                super.setupAnim(state);
                root().xScale = 1.2F;
                body.yScale = state.ageInTicks;
                body.visible = state.ageInTicks != 3;
                body.skipDraw = state.ageInTicks == 4;
            }
        };
        replacement = animated;
        resolutions = 0;
        HumanoidRenderState state = new HumanoidRenderState();
        state.ageInTicks = 1;
        BufferedEquipment output = new BufferedEquipment(true);
        SpikeChecks.require(PairRenderer.render(SpikeClient.nativeRenderer, original, state,
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                Fixture.source(CUSTOM.get(), EquipmentAssets.IRON), Fixture.source(CUSTOM.get(), EquipmentAssets.GOLD),
                new PoseStack(), output, 15728880, 0), "Native replacement rejected");
        SpikeChecks.require(resolutions == 2, "Native extension was resolved more than once per source");
        Model<HumanoidRenderState> left = (Model<HumanoidRenderState>) output.snapshot().getFirst().model();
        var first = SpikeChecks.vertices(left, state);
        state.ageInTicks = 2;
        SpikeChecks.require(!first.equals(SpikeChecks.vertices(left, state)), "Deferred source animation lost");
        state.ageInTicks = 1;
        SpikeChecks.require(first.equals(SpikeChecks.vertices(left, state)), "A → B → A leaked native state");
        for (int age : new int[]{3, 4}) {
            state.ageInTicks = age;
            var view = new LeggingsView<>(animated, true);
            view.setupAnim(state);
            SpikeChecks.require(view.root().getChild("waist").visible == animated.body.visible
                    && view.root().getChild("waist").skipDraw == animated.body.skipDraw
                    && view.root().getChild("waist").yScale == animated.body.yScale
                    && view.root().xScale == animated.root().xScale, "Scale/visibility/root transform lost");
        }

        replacement = new Model<>(original.root(), RenderTypes::armorCutoutNoCull) {};
        resolutions = 0;
        BufferedEquipment rejected = new BufferedEquipment(true);
        SpikeChecks.require(!PairRenderer.render(SpikeClient.nativeRenderer, original, state,
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                Fixture.source(Items.IRON_LEGGINGS, EquipmentAssets.IRON),
                Fixture.source(UNSUPPORTED.get(), EquipmentAssets.GOLD), new PoseStack(), rejected, 15728880, 0),
                "Non-humanoid replacement accepted");
        SpikeChecks.require(rejected.size() == 0 && resolutions == 1, "Rejected right source leaked left nodes");
        return "neoforge.nativeReplacementOnce=true\nneoforge.deferredScalesAndFlags=true\n"
                + "neoforge.unsupportedRightAtomic=true\n" + verifyIceAndFire(original);
    }

    private static String verifyIceAndFire(HumanoidModel<HumanoidRenderState> original) {
        if (!ModList.get().isLoaded("iceandfire")) { return ""; }
        int replacements = 0;
        int rejected = 0;
        int decorated = 0;
        StringBuilder details = new StringBuilder();
        for (Item item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("iceandfire") || !id.getPath().endsWith("_leggings")) { continue; }
            ItemStack source = Fixture.source(item, EquipmentAssets.IRON);
            Model<?> resolved = IClientItemExtensions.of(source).getGenericArmorModel(source,
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, original);
            if (resolved == original) { continue; }
            replacements++;
            SpikeChecks.require(resolved instanceof HumanoidModel<?>, "Unexpected Ice and Fire non-humanoid");
            @SuppressWarnings("unchecked")
            HumanoidModel<HumanoidRenderState> nativeModel = (HumanoidModel<HumanoidRenderState>) resolved;
            Map<String, ModelPart> descendants = waistDescendants(nativeModel.body);
            if (!descendants.isEmpty()) {
                decorated++;
                details.append(id).append(" descendants=").append(descendants.keySet()).append('\n');
                descendants.forEach((path, part) -> details.append("  ").append(path).append(" reference=")
                        .append(part.getInitialPose()).append('\n'));
            }
            for (HumanoidRenderState pose : controlPoses()) {
                SpikeChecks.assertModel(nativeModel, pose);
                descendants.forEach((path, part) -> SpikeChecks.require(livePose(part).equals(part.getInitialPose()),
                        "Ice and Fire child animates independently of its reference pose: " + id + "/" + path));
            }
            BufferedEquipment destination = new BufferedEquipment(true);
            boolean supported = PairRenderer.render(SpikeClient.nativeRenderer, original, new HumanoidRenderState(),
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                    Fixture.source(Items.IRON_LEGGINGS, EquipmentAssets.IRON), source,
                    new PoseStack(), destination, 15728880, 0);
            if (!supported) {
                SpikeChecks.require(destination.size() == 0, "Ice and Fire left a partially rendered pair");
                rejected++;
                details.append(id).append(": ").append(PairRenderer.rejectionReason).append('\n');
            }
        }
        SpikeChecks.require(replacements > 0, "Ice and Fire control found no native replacements");
        SpikeChecks.require(replacements == 30 && decorated == 13 && rejected == 0,
                "Pinned Ice and Fire hierarchy coverage changed unexpectedly");
        return "iceandfire.replacements=" + replacements + "\niceandfire.decoratedWaists=" + decorated
                + "\niceandfire.unsupportedWaists=" + rejected + "\niceandfire.nativePoseAreaAndChildren=true\n" + details;
    }

    private static Map<String, ModelPart> waistDescendants(ModelPart waist) {
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        waist.visit(new PoseStack(), (pose, path, index, cube) -> {
            ModelPart part = waist;
            String currentPath = "";
            if (path.isEmpty()) { return; }
            for (String name : path.substring(1).split("/")) {
                part = part.getChild(name);
                currentPath += "/" + name;
                parts.put(currentPath, part);
            }
        });
        return parts;
    }

    private static PartPose livePose(ModelPart part) {
        return new PartPose(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                part.xScale, part.yScale, part.zScale);
    }

    private static List<HumanoidRenderState> controlPoses() {
        HumanoidRenderState walking = new HumanoidRenderState();
        walking.walkAnimationPos = 1.4F;
        walking.walkAnimationSpeed = 0.7F;
        HumanoidRenderState crouching = new HumanoidRenderState();
        crouching.isCrouching = true;
        HumanoidRenderState seated = new HumanoidRenderState();
        seated.isPassenger = true;
        ArmorStandRenderState stand = new ArmorStandRenderState();
        stand.bodyPose = new Rotations(18, 25, -10);
        stand.leftLegPose = new Rotations(-35, 10, -10);
        stand.rightLegPose = new Rotations(25, -10, 12);
        return List.of(new HumanoidRenderState(), walking, crouching, seated, stand);
    }

    static void prepareGallery() {
        if (!ModList.get().isLoaded("iceandfire")) { return; }
        ItemStack silver = sample("armor_silver_metal_leggings", "silver");
        ItemStack dragon = sample("armor_red_leggings", "dragon_scales_red");
        ItemStack iron = Fixture.source(Items.IRON_LEGGINGS, EquipmentAssets.IRON);
        ItemStack gold = Fixture.source(Items.GOLDEN_LEGGINGS, EquipmentAssets.GOLD);
        Fixture.extraSources.put(100, new ItemStack[]{silver, iron});
        Fixture.extraSources.put(101, new ItemStack[]{iron, silver});
        Fixture.extraSources.put(102, new ItemStack[]{dragon, gold});
        Fixture.extraSources.put(103, new ItemStack[]{gold, dragon});
        Fixture.extraSources.put(104, new ItemStack[]{dragon, silver});
        Fixture.extraSources.put(105, new ItemStack[]{silver, dragon});
        Fixture.gallery = List.of(new Fixture.Sample("Native silver", silver, 0),
                new Fixture.Sample("Silver / iron", Fixture.pair(100), 0),
                new Fixture.Sample("Iron / silver (back)", Fixture.pair(101), (float) Math.PI),
                new Fixture.Sample("Native red dragon", dragon, 0),
                new Fixture.Sample("Dragon / gold", Fixture.pair(102), 0),
                new Fixture.Sample("Gold / dragon (back)", Fixture.pair(103), (float) Math.PI),
                new Fixture.Sample("Dragon / silver (side)", Fixture.pair(104), (float) Math.PI / 2),
                new Fixture.Sample("Silver / dragon (posed)", Fixture.pair(105), 0));
    }

    private static ItemStack sample(String itemId, String assetId) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("iceandfire", itemId));
        SpikeChecks.require(item != null && item != Items.AIR, "Missing gallery item: " + itemId);
        return Fixture.source(item, ResourceKey.create(EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath("iceandfire", assetId)));
    }
}
