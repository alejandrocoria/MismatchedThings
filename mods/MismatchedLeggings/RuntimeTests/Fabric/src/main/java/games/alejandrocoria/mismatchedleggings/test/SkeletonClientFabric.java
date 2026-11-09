package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedChecks;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SkeletonClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (games.alejandrocoria.mismatchedleggings.test.mysticaleffects.EffectsChecks.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(games.alejandrocoria.mismatchedleggings.test.mysticaleffects.EffectsClient::tick);
            return;
        }
        if (Boolean.getBoolean("leggings.test.betterEnd")) {
            BetterEndClientChecks.renderChecks = CrystaliteCaptureChecks::run;
        }
        if (Boolean.getBoolean("leggings.test.items")) {
            FabricItemChecks.install();
        }
        if (Boolean.getBoolean("leggings.test.equipped") || Boolean.getBoolean("leggings.test.betterEnd")) {
            ArmorRenderer.register((pose, collector, stack, state, slot, light, model) -> {
                throw new AssertionError("Mixed rendering invoked an unsafe custom renderer");
            }, Items.CHAINMAIL_LEGGINGS);
        }
        if (Boolean.getBoolean("leggings.test.equipped")) {
            EquippedClient.platformChecks = () -> {
                var model = new HumanoidModel<HumanoidRenderState>(Minecraft.getInstance().getEntityModels()
                        .bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
                var custom = new ItemStack(Items.CHAINMAIL_LEGGINGS);
                var iron = new ItemStack(Items.IRON_LEGGINGS);
                EquippedChecks.require(!EquipmentSources.INSTANCE.usesStandardRenderer(custom), "Custom Fabric renderer not detected");
                for (boolean customOwner : new boolean[]{false, true}) {
                    var output = new RecordingSubmits();
                    MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, new HumanoidRenderState(),
                            EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                            RenderFixture.pair(customOwner ? custom : iron, customOwner ? iron : custom),
                            new PoseStack(), output, 15728880, 0);
                    EquippedChecks.require(output.nodes.size() == 1 && !(output.nodes.getFirst().model() instanceof LeggingsView<?>),
                            "Fabric custom renderer did not choose a complete fallback");
                }
                return "fabric.customRendererFallback=true";
            };
        }
        if (Boolean.getBoolean("leggings.test.createFly")) {
            ClientTickEvents.END_CLIENT_TICK.register(CreateFlyClientChecks::tick);
        } else {
            ClientTickEvents.END_CLIENT_TICK.register(SkeletonClientChecks::tick);
        }
    }
}
