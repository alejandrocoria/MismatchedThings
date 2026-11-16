package games.alejandrocoria.mismatchedboots.runtimetest.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Checks the baked native armor layer, not a direct call to the Boots equipment renderer. */
public final class ArmorLayerEntryChecks {
    private static final int LIGHT = 0x00F000F0;
    private static final List<Call> CALLS = new ArrayList<>();
    private static boolean active;
    private static int lookups;
    private static int providers;
    private static HumanoidModel<?> feetModel;

    private ArmorLayerEntryChecks() {
    }

    public static void recordLookup(EquipmentSlot slot, HumanoidModel<?> model) {
        if (active && slot == EquipmentSlot.FEET) {
            lookups++;
            feetModel = model;
        }
    }

    public static void recordProvider() {
        if (active) {
            providers++;
        }
    }

    public static void recordCall(EquipmentClientInfo.LayerType layer, Model<?> model,
                                  ItemStack stack, int light, int outline) {
        if (active) {
            CALLS.add(new Call(stack.copy(), layer, model, light, outline));
        }
    }

    public static String run(Minecraft client, boolean neoForge) {
        try {
            for (HumanoidRenderState state : states()) {
                state.headEquipment = new ItemStack(Items.IRON_HELMET);
                state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
                state.legsEquipment = new ItemStack(Items.IRON_LEGGINGS);
                state.outlineColor = 0xFF123456;

                state.feetEquipment = new ItemStack(Items.IRON_BOOTS);
                submit(client, state, neoForge);
                require(CALLS.size() == 4 && lookups == 1, "Native armor must retain four ordinary draws");
                require(CALLS.stream().filter(call -> call.stack.is(Items.IRON_BOOTS)).allMatch(call -> call.model == feetModel),
                        "Native armor was claimed by a mixed entry");
                var expectedModel = feetModel;

                for (boolean inverse : List.of(false, true)) {
                    var first = inverse ? Items.GOLDEN_BOOTS : Items.IRON_BOOTS;
                    var second = inverse ? Items.IRON_BOOTS : Items.GOLDEN_BOOTS;
                    state.feetEquipment = pair(client, new ItemStack(first), new ItemStack(second));
                    var before = snapshot(client, state.feetEquipment);
                    submit(client, state, neoForge);

                    require(CALLS.size() == 5 && lookups == 1, "Split duplicated or omitted an armor draw");
                    var feet = CALLS.stream().filter(call -> call.stack.is(first) || call.stack.is(second)).toList();
                    require(feet.size() == 2 && feet.get(0).stack.is(first) && feet.get(1).stack.is(second),
                            "Actual entry changed source submission order");
                    require(feetModel == expectedModel && feet.get(0).model != feetModel && feet.get(1).model != feetModel
                                    && feet.get(0).model.root() == feetModel.leftLeg && feet.get(1).model.root() == feetModel.rightLeg,
                            "Actual entry did not retain native age-model selection and distinct owned foot views");
                    require(CALLS.stream().noneMatch(call -> ModContent.isMixedBoots(call.stack)), "Root leaked into split draw");
                    require(CALLS.stream().filter(call -> call.stack.is(Items.IRON_HELMET)
                                    || call.stack.is(Items.IRON_CHESTPLATE) || call.stack.is(Items.IRON_LEGGINGS)).count() == 3,
                            "Mixed FEET entry intercepted another native slot");
                    var layer = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                                ? EquipmentClientInfo.LayerType.HUMANOID_BABY : EquipmentClientInfo.LayerType.HUMANOID;
                    require(feet.stream().allMatch(call -> call.layer == layer), "Native age/armor-stand layer changed");
                    require(before.equals(snapshot(client, state.feetEquipment)), "Entry changed root/source histories");
                }
            }

            edgeCases(client, neoForge);
            materials(client, neoForge);
            return "equipment.entry.nativeLayer=true\nequipment.entry.ownerFallbackOnce=true\n"
                    + "equipment.entry.declinedModelLookups=2\nequipment.entry.readOnlyHistories=true\n"
                    + "equipment.entry.nativeProviders=" + neoForge + "\n";
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot obtain native baked armor layer", failure);
        }
    }

    /** Optional pinned replacements, through the same real layer entry as the vanilla controls. */
    public static String customModels(Minecraft client, List<String> samples) {
        StringBuilder report = new StringBuilder();
        try {
            var state = new ZombieRenderState();
            state.entityType = EntityType.ZOMBIE;
            state.isCrouching = true;
            state.outlineColor = 0xFF123456;
            state.feetEquipment = new ItemStack(Items.IRON_BOOTS);
            int ordinaryNodes = submit(client, state, true).nodes.size();
            require(ordinaryNodes > 0, "Ordinary native foot control has no material nodes");

            for (String sample : samples) {
                Identifier id = Identifier.parse(sample);
                require(BuiltInRegistries.ITEM.containsKey(id), "Missing pinned custom-model sample: " + sample);
                var source = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                var sourceBefore = snapshot(client, source);
                state.feetEquipment = source;
                var nativeOutput = submit(client, state, true);
                require(CALLS.size() == 1 && !nativeOutput.nodes.isEmpty()
                                && nativeOutput.nodes.stream().allMatch(node -> node.model != feetModel),
                        "Native custom-model positive did not replace the original model: " + sample);

                for (boolean inverse : List.of(false, true)) {
                    var ordinary = new ItemStack(Items.IRON_BOOTS);
                    state.feetEquipment = inverse ? pair(client, ordinary, source) : pair(client, source, ordinary);
                    var before = snapshot(client, state.feetEquipment);
                    var output = submit(client, state, true);
                    require(CALLS.size() == 2 && lookups == 1 && providers == 2,
                            "Custom source was omitted or resolved twice: " + sample);
                    require(CALLS.get(inverse ? 1 : 0).stack.is(source.getItem())
                                    && CALLS.get(inverse ? 0 : 1).stack.is(Items.IRON_BOOTS),
                            "Custom source submission order changed: " + sample);
                    require(output.nodes.size() == nativeOutput.nodes.size() + ordinaryNodes,
                            "Custom source lost or duplicated native material nodes: " + sample);
                    require(output.nodes.stream().allMatch(node ->
                                    node.model.getClass().getEnclosingClass() == MixedBootsEquipmentRenderer.class
                                            && node.model.root().visible
                                            && node.model.root().getAllParts().stream().anyMatch(part -> part.visible && !part.isEmpty()))
                                    && output.nodes.stream().map(node -> node.model.root()).distinct().count() == 2,
                            "Late replacement lost its distinct visible owned foot views: " + sample);
                    require(before.equals(snapshot(client, state.feetEquipment)) && sourceBefore.equals(snapshot(client, source)),
                            "Custom model entry changed source history: " + sample);
                }
                report.append("equipment.entry.custom.").append(sample).append("=2\n");
            }
            return report.toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot obtain native custom-model armor layer", failure);
        }
    }

    private static void edgeCases(Minecraft client, boolean neoForge) throws ReflectiveOperationException {
        var state = new ZombieRenderState();
        state.entityType = EntityType.ZOMBIE;
        var unsupported = new ItemStack(Items.GOLDEN_BOOTS);
        unsupported.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        state.feetEquipment = pair(client, new ItemStack(Items.IRON_BOOTS), unsupported);
        var before = snapshot(client, state.feetEquipment);
        submit(client, state, neoForge);
        require(CALLS.size() == 1 && ModContent.isMixedBoots(CALLS.getFirst().stack) && lookups == 2,
                "Declined split must fall through to exactly one native owner draw and two model-set lookups");
        require(before.equals(snapshot(client, state.feetEquipment)), "Fallback changed source histories");

        var valid = pair(client, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        var foreign = new ItemStack(Items.DIAMOND_BOOTS);
        foreign.applyComponents(valid.getComponentsPatch());
        state.feetEquipment = foreign;
        submit(client, state, neoForge);
        require(CALLS.size() == 1 && CALLS.getFirst().stack.is(Items.DIAMOND_BOOTS) && lookups == 1
                        && CALLS.getFirst().model == feetModel,
                "Foreign copied components acquired a mixed-item rendering claim");

        valid.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        state.feetEquipment = valid;
        require(!HumanoidArmorLayer.shouldRender(valid, EquipmentSlot.FEET), "Boots bypassed native missing-asset admission");
        submit(client, state, neoForge);
        require(CALLS.isEmpty() && lookups == 0, "Missing owner asset acquired a new fallback");

        state.feetEquipment = ItemStack.EMPTY;
        state.headEquipment = pair(client, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        submit(client, state, neoForge);
        require(CALLS.isEmpty() && lookups == 0, "Boots claimed the wrong equipment slot");
        state.headEquipment = ItemStack.EMPTY;

        state.feetEquipment = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        submit(client, state, neoForge);
        require(CALLS.isEmpty(), "Bare root acquired a new draw");
        state.feetEquipment.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_BOOTS).get(DataComponents.EQUIPPABLE));
        submit(client, state, neoForge);
        require(CALLS.size() == 1 && ModContent.isMixedBoots(CALLS.getFirst().stack) && lookups == 2,
                "Invalid data with an explicit owner asset lost ordinary fall-through");

        var raw = snapshot(client, pair(client, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
        raw.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                .getCompoundOrEmpty("left").putString("id", "equipment_entry_test:missing_boots");
        var ops = client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        state.feetEquipment = ItemStack.CODEC.parse(ops, raw).getOrThrow();
        require(ModContent.getMixedBootsData(state.feetEquipment).isEmpty()
                        && ModContent.suspendedBootsData(state.feetEquipment).isPresent(),
                "Missing-source control did not actually suspend");
        before = snapshot(client, state.feetEquipment);
        submit(client, state, neoForge);
        require(CALLS.isEmpty() && before.equals(snapshot(client, state.feetEquipment)), "Suspended root changed its rendering/data");
    }

    private static void materials(Minecraft client, boolean neoForge) throws ReflectiveOperationException {
        var leather = new ItemStack(Items.LEATHER_BOOTS);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
        leather.set(DataComponents.TRIM, new ArmorTrim(
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.QUARTZ),
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.COAST)));
        var state = new ZombieRenderState();
        state.entityType = EntityType.ZOMBIE;
        state.feetEquipment = pair(client, leather, new ItemStack(Items.GOLDEN_BOOTS));
        state.feetEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        state.outlineColor = 0xFF123456;
        var before = snapshot(client, state.feetEquipment);
        var output = submit(client, state, neoForge);
        require(CALLS.size() == 2 && CALLS.stream().allMatch(call -> call.stack.hasFoil())
                        && CALLS.getFirst().stack.get(DataComponents.DYED_COLOR).rgb() == 0x3366CC,
                "Actual entry lost live foil or source dye");
        require(output.nodes.size() > 2 && output.nodes.stream().anyMatch(node -> node.sprite != null),
                "Native material/trim/foil submissions are missing");
        require(before.equals(snapshot(client, state.feetEquipment)), "Material draw changed source histories");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecordingSubmits submit(Minecraft client, HumanoidRenderState state, boolean neoForge)
            throws ReflectiveOperationException {
        var renderer = client.getEntityRenderDispatcher().getRenderer(state);
        require(renderer instanceof LivingEntityRenderer, "No native living renderer for " + state.getClass());
        var field = LivingEntityRenderer.class.getDeclaredField("layers");
        field.setAccessible(true);
        var layers = (List<?>) field.get(renderer);
        var armor = (HumanoidArmorLayer) layers.stream().filter(HumanoidArmorLayer.class::isInstance).findFirst().orElseThrow();
        var output = new RecordingSubmits();
        CALLS.clear();
        lookups = 0;
        providers = 0;
        feetModel = null;
        active = true;
        try {
            armor.submit(new PoseStack(), output, LIGHT, state, 0, 0);
            output.nodes.forEach(Node::animate);
        } finally {
            active = false;
        }
        require(CALLS.stream().allMatch(call -> call.light == LIGHT && call.outline == state.outlineColor),
                "Native entry lost light/outline");
        if (neoForge) {
            require(providers == CALLS.size(), "Native extensions resolved twice or were bypassed: " + providers + "/" + CALLS.size());
        }
        return output;
    }

    private static List<HumanoidRenderState> states() {
        var adult = new ZombieRenderState();
        adult.entityType = EntityType.ZOMBIE;
        adult.isCrouching = true;
        var baby = new ZombieRenderState();
        baby.entityType = EntityType.ZOMBIE;
        baby.isBaby = true;
        var stand = new ArmorStandRenderState();
        stand.entityType = EntityType.ARMOR_STAND;
        var small = new ArmorStandRenderState();
        small.entityType = EntityType.ARMOR_STAND;
        small.isBaby = true;
        small.isSmall = true;
        var wide = player("WIDE");
        wide.walkAnimationPos = 1.4F;
        wide.walkAnimationSpeed = .7F;
        var slim = player("SLIM");
        slim.isPassenger = true;
        return List.of(adult, baby, stand, small, wide, slim);
    }

    private static AvatarRenderState player(String skinModel) {
        var player = new AvatarRenderState();
        player.entityType = EntityType.PLAYER;
        for (int index = 0; index < 32; index++) {
            player.skin = DefaultPlayerSkin.get(new UUID(0, index));
            if (player.skin.model().name().equals(skinModel)) {
                return player;
            }
        }
        throw new AssertionError("No native default skin for " + skinModel);
    }

    private static ItemStack pair(Minecraft client, ItemStack left, ItemStack right) {
        return MixedBootsFactory.createPair(left, right, client.level.registryAccess()).primary();
    }

    private static CompoundTag snapshot(Minecraft client, ItemStack stack) {
        var ops = client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Call(ItemStack stack, EquipmentClientInfo.LayerType layer, Model<?> model, int light, int outline) {
    }

    private record Node<S>(Model<? super S> model, S state, TextureAtlasSprite sprite) {
        void animate() {
            model.setupAnim(state);
        }
    }

    private static final class RecordingSubmits extends SubmitNodeStorage {
        private final List<Node<?>> nodes = new ArrayList<>();

        @Override
        public SubmitNodeCollection order(int order) {
            return new SubmitNodeCollection(this) {
                @Override
                public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type,
                                            int light, int overlay, int color, TextureAtlasSprite sprite,
                                            int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
                    nodes.add(new Node<>(model, state, sprite));
                }
            };
        }
    }
}
