package games.alejandrocoria.leggingsspike;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class FabricSpike implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpikeClient.withBoots = FabricLoader.getInstance().isModLoaded("mismatchedboots");
        // Test-only native renderer. Registry lookup is internal Fabric API, not a new
        // supported production contract; the spike establishes the required boundary.
        ArmorRenderer.register((pose, collector, stack, state, slot, light, model) -> {}, Items.CHAINMAIL_LEGGINGS);
        PairRenderer.supportsSource = stack -> ArmorRendererRegistryImpl.get(stack.getItem()) == null;
        SpikeClient.extraChecks = () -> {
            var model = new HumanoidModel<HumanoidRenderState>(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
            BufferedEquipment destination = new BufferedEquipment(true);
            SpikeChecks.require(ArmorRendererRegistryImpl.get(Items.CHAINMAIL_LEGGINGS) != null,
                    "Synthetic Fabric custom renderer was not loaded");
            SpikeChecks.require(!PairRenderer.render(SpikeClient.nativeRenderer, model, new HumanoidRenderState(),
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                    Fixture.source(Items.IRON_LEGGINGS, EquipmentAssets.IRON),
                    Fixture.source(Items.CHAINMAIL_LEGGINGS, EquipmentAssets.CHAINMAIL), new PoseStack(), destination,
                    15728880, 0) && destination.size() == 0, "Fabric custom renderer silently bypassed");
            return "fabric.customRendererRejectedAtomically=true\n";
        };
        ClientTickEvents.END_CLIENT_TICK.register(SpikeClient::tick);
    }
}
