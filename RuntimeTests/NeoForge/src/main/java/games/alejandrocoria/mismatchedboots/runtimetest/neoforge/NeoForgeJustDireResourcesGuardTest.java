package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Real startup fallback, independent of the accepted passive Jump adapter. */
final class NeoForgeJustDireResourcesGuardTest {
    private static boolean finished;
    private NeoForgeJustDireResourcesGuardTest() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; }
            finished = true;
            String variant = System.getProperty("mismatchedboots.runtimetest.justDireResourcesGuard");
            try {
                ItemStack source = variant.equals("absent") ? new ItemStack(Items.GOLDEN_BOOTS)
                        : NeoForgeJustDireResourcesTest.boots("celestigem", 6_000);
                ItemStack before = source.copy();
                var pair = MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS));
                for (ItemStack root : new ItemStack[]{pair.primary(), pair.inverse()}) {
                    ItemStack rootBefore = root.copy();
                    for (int repeat = 0; repeat < 3; repeat++) {
                        require(JustDireResourceTestSupport.handler(root) == null, "Failed/absent API still exposed mixed storage");
                    }
                    require(ItemStack.matches(rootBefore, root), "Fallback lookup modified generic root state");
                }
                require(ItemStack.matches(before, source), "Fallback modified input state");
                if (variant.equals("absent")) {
                    require(BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("justdirethings", "celestigem_boots")).isEmpty(),
                            "Absent fixture unexpectedly installed native mod");
                } else {
                    require(JustDireResourceTestSupport.handler(source).getAmountAsInt() == 6_000,
                            "Incompatible binding fixture broke native JDT rather than only the adapter");
                    ItemStack original = NeoForgeJustDireJumpTest.boots("ferricore", true);
                    ItemStack mixed = MixedBootsFactory.createPair(original, new ItemStack(Items.IRON_BOOTS)).primary();
                    var player = new ServerPlayer(event.getServer(), event.getServer().overworld(),
                            new GameProfile(UUID.randomUUID(), "resources-guard"), ClientInformation.createDefault());
                    player.setItemSlot(EquipmentSlot.FEET, original);
                    player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
                    double nativeJump = player.getDeltaMovement().y;
                    player.setItemSlot(EquipmentSlot.FEET, mixed);
                    player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
                    require(Math.abs(nativeJump - player.getDeltaMovement().y) < 1e-7, "Storage API failure disabled independent 8.2 Jump");
                }
                NetworkAcceptanceFiles.write("just-dire-resources-guard-report.txt", variant + "; production startup fallback; no mixed capability; "
                        + "read-only repeated lookup/input preservation; " + (variant.equals("absent") ? "optional mod absent" : "native FE intact; independent 8.2 Jump intact") + "\n");
                NetworkAcceptanceFiles.write("just-dire-resources-guard-success.txt", variant + "\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-resources-guard", failure); }
            finally { event.getServer().halt(false); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
