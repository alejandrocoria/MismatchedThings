package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

@SuppressWarnings("UnstableApiUsage")
public final class PersistenceClientGameTest implements FabricClientGameTest {
    private static final String OPAQUE_VALUE = "client-alpha";

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runOnServer(server -> {
                ItemStack inventoryStack = mixedWithOpaqueOwner();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().add(inventoryStack);
                ItemEntity dropped = new ItemEntity(
                        server.overworld(), player.getX() + 2.0, player.getY(), player.getZ(), mixedWithOpaqueOwner());
                dropped.setNeverPickUp();
                server.overworld().addFreshEntity(dropped);
            });
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.player != null
                    && client.player.getInventory().contains(PersistenceClientGameTest::hasExpectedData)
                    && client.level.getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(client.player.blockPosition()).inflate(6.0),
                            entity -> hasExpectedData(entity.getItem())).size() == 1);
            context.runOnClient(client -> {
                ItemStack equipped = client.player.getItemBySlot(EquipmentSlot.FEET);
                if (!equipped.isEmpty()) {
                    throw new AssertionError("Persistence sync fixture unexpectedly changed equipment");
                }
            });
        }
    }

    private static ItemStack mixedWithOpaqueOwner() {
        ItemStack owner = new ItemStack(TestContent.requireComponentBoots());
        owner.set(TestContent.requireOpaqueComponent(), OPAQUE_VALUE);
        return MixedBootsFactory.createPair(owner, new ItemStack(Items.DIAMOND_BOOTS)).primary();
    }

    private static boolean hasExpectedData(ItemStack stack) {
        return stack.is(ModContent.MIXED_BOOTS_KEY)
                && OPAQUE_VALUE.equals(stack.get(TestContent.requireOpaqueComponent()))
                && ModContent.getMixedBootsData(stack)
                        .filter(data -> data.left().source().is(TestContent.requireComponentBoots())
                                && OPAQUE_VALUE.equals(data.left().source().get(TestContent.requireOpaqueComponent()))
                                && data.right().source().is(Items.DIAMOND_BOOTS))
                        .isPresent();
    }
}
