package games.alejandrocoria.mismatchedleggings.test.render;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/** Native final-model resolution and pinned decorated controls against production capture/fallback. */
public final class NativeEquipmentChecks {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("leggings_skeleton_test");
    private static final Supplier<Item> CUSTOM = ITEMS.registerSimpleItem("render_custom");
    private static Model<HumanoidRenderState> replacement;
    private static int resolutions;

    private NativeEquipmentChecks() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((RegisterClientExtensionsEvent event) -> event.registerItem(new IClientItemExtensions() {
            @Override
            @SuppressWarnings("rawtypes")
            public Model getGenericArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layer, Model original) {
                resolutions++;
                return replacement;
            }
        }, CUSTOM.get()));
        EquippedClient.platformChecks = NativeEquipmentChecks::verify;
        EquippedClient.prepareGallery = NativeEquipmentChecks::gallery;
    }

    private static ItemStack custom() {
        var stack = new ItemStack(CUSTOM.get());
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.MAX_DAMAGE, 100);
        stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).setAsset(EquipmentAssets.IRON).build());
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static String verify() {
        var models = Minecraft.getInstance().getEntityModels();
        var original = new HumanoidModel<HumanoidRenderState>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        var animated = new HumanoidModel<HumanoidRenderState>(models.bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs())) {
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
        var output = new RecordingSubmits();
        var state = new HumanoidRenderState();
        state.ageInTicks = 1;
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, original, state,
                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, RenderFixture.pair(custom(), custom()),
                new PoseStack(), output, 15728880, 0);
        EquippedChecks.require(resolutions == 2 && output.nodes.size() == 2, "Native extensions did not resolve once per source");
        var left = (Model<HumanoidRenderState>) output.nodes.getFirst().model();
        var first = EquippedChecks.vertices(left, state);
        state.ageInTicks = 2;
        EquippedChecks.require(!first.equals(EquippedChecks.vertices(left, state)), "Deferred native animation was lost");
        state.ageInTicks = 1;
        EquippedChecks.require(first.equals(EquippedChecks.vertices(left, state)), "Queued native A → B → A leaked state");
        for (int age : new int[]{3, 4}) {
            state.ageInTicks = age;
            left.setupAnim(state);
            var waist = left.root().getChild("waist");
            EquippedChecks.require(waist.visible == animated.body.visible && waist.skipDraw == animated.body.skipDraw
                    && waist.yScale == animated.body.yScale && left.root().xScale == animated.root().xScale,
                    "Native flags or scales were lost");
        }

        replacement = new Model<>(original.root(), RenderTypes::armorCutoutNoCull) {};
        for (boolean customOwner : new boolean[]{false, true}) {
            resolutions = 0;
            var fallback = new RecordingSubmits();
            var iron = new ItemStack(Items.IRON_LEGGINGS);
            MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, original, new HumanoidRenderState(),
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                    RenderFixture.pair(customOwner ? custom() : iron, customOwner ? iron : custom()),
                    new PoseStack(), fallback, 15728880, 0);
            EquippedChecks.require(resolutions == 1 && fallback.nodes.size() == 1
                    && !(fallback.nodes.getFirst().model() instanceof LeggingsView<?>),
                    "Unsupported native model leaked a half-pair or resolved twice");
        }
        pinnedModels(original);
        return "neoforge.finalModelsAndFallback=true";
    }

    @SuppressWarnings("unchecked")
    private static void pinnedModels(HumanoidModel<HumanoidRenderState> original) {
        if (!ModList.get().isLoaded("iceandfire")) {
            return;
        }
        int replacements = 0;
        int decorated = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("iceandfire") || !id.getPath().endsWith("_leggings")) {
                continue;
            }
            var source = new ItemStack(item);
            Model<?> model = IClientItemExtensions.of(source).getGenericArmorModel(source,
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, original);
            if (model == original) {
                continue;
            }
            replacements++;
            EquippedChecks.require(model instanceof HumanoidModel<?>, "Pinned model stopped being humanoid");
            var humanoid = (HumanoidModel<HumanoidRenderState>) model;
            if (humanoid.body.getAllParts().size() > 1) {
                decorated++;
            }
            for (var state : List.of(new HumanoidRenderState(), crouching(), seated())) {
                EquippedChecks.assertModel(humanoid, state);
            }
            var output = new RecordingSubmits();
            MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, original, new HumanoidRenderState(),
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                    RenderFixture.pair(new ItemStack(Items.IRON_LEGGINGS), source), new PoseStack(), output, 15728880, 0);
            EquippedChecks.require(output.nodes.size() >= 2 && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                    "Pinned decorated source fell back: " + id);
        }
        EquippedChecks.require(replacements == 30 && decorated == 13, "Pinned decorated coverage changed");
        IceAndFireRenderingChecks.verify(original);
        games.alejandrocoria.mismatchedleggings.test.IceAndFireTooltipChecks.verify();
    }

    private static HumanoidRenderState crouching() {
        var state = new HumanoidRenderState();
        state.isCrouching = true;
        return state;
    }

    private static HumanoidRenderState seated() {
        var state = new HumanoidRenderState();
        state.isPassenger = true;
        return state;
    }

    private static void gallery() {
        if (!ModList.get().isLoaded("iceandfire")) {
            return;
        }
        var silver = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("iceandfire:armor_silver_metal_leggings")));
        var dragon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("iceandfire:armor_red_leggings")));
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        var gold = new ItemStack(Items.GOLDEN_LEGGINGS);
        RenderFixture.gallery = List.of(new RenderFixture.Sample("Native silver", silver, 0),
                new RenderFixture.Sample("Silver / iron", RenderFixture.pair(silver, iron), 0),
                new RenderFixture.Sample("Iron / silver (back)", RenderFixture.pair(iron, silver), (float) Math.PI),
                new RenderFixture.Sample("Native dragon", dragon, 0),
                new RenderFixture.Sample("Dragon / gold", RenderFixture.pair(dragon, gold), 0),
                new RenderFixture.Sample("Gold / dragon (back)", RenderFixture.pair(gold, dragon), (float) Math.PI),
                new RenderFixture.Sample("Dragon / silver (side)", RenderFixture.pair(dragon, silver), (float) Math.PI / 2),
                new RenderFixture.Sample("Silver / dragon posed", RenderFixture.pair(silver, dragon), 0));
    }
}
