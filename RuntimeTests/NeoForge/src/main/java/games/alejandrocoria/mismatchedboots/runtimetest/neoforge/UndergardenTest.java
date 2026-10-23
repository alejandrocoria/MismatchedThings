package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Observe real native block handlers and collision decisions; no replacement effect engine. */
public final class UndergardenTest {
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.undergarden", "");
    private static final BlockPos POS = new BlockPos(0, 80, 0);
    private static boolean finished;
    private static int peers, cases;
    private static final StringBuilder REPORT = new StringBuilder();

    private UndergardenTest() {}

    public static boolean observe(boolean original) { peers++; return original; }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            boolean absent = MODE.equals("absent"), refused = MODE.equals("refused");
            require(ModList.get().isLoaded("undergarden") != absent, "Wrong optional-mod environment");
            ServerLevel level = server.overworld();
            var owner = player(level);
            var ordinary = paid(owner, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            snow(owner, new ItemStack(Items.LEATHER_BOOTS), true);
            for (var boots : ordinary) { snow(owner, boots, false); }
            if (!absent) {
                ItemStack cloggrum = boot("cloggrum"), froststeel = boot("froststeel"), utherium = boot("utherium");
                var stacks = new ArrayList<ItemStack>(List.of(cloggrum, froststeel, utherium));
                for (var nativeBoot : List.of(cloggrum, froststeel, utherium)) {
                    stacks.addAll(paid(owner, nativeBoot, new ItemStack(Items.DIAMOND_BOOTS)));
                }
                stacks.addAll(paid(owner, cloggrum, froststeel));
                stacks.addAll(paid(owner, cloggrum, cloggrum));
                stacks.addAll(paid(owner, froststeel, froststeel));
                stacks.addAll(ordinary);
                ItemStack used = stacks.get(3);
                ItemStack count = used.copy(); count.setCount(2); stacks.add(count);
                ItemStack bare = used.copy(); bare.remove(ModContent.mixedBootsDataType().orElseThrow()); stacks.add(bare);
                ItemStack spoof = new ItemStack(Items.STICK);
                spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(used).orElseThrow()); stacks.add(spoof);
                ItemStack badSnow = stacks.get(5).copy(); badSnow.setCount(2); stacks.add(badSnow);
                ItemStack bareSnow = stacks.get(5).copy(); bareSnow.remove(ModContent.mixedBootsDataType().orElseThrow()); stacks.add(bareSnow);
                Block layer = block("goo"), full = block("goo_block");
                for (ItemStack boots : stacks) {
                    boolean mixed = ModContent.getMixedBootsData(boots).isPresent() && boots.getCount() == 1
                            && boots.getItem() instanceof games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
                    boolean clog = mixed && hasSource(boots, cloggrum) || boots.is(cloggrum.getItem());
                    boolean frost = mixed && hasSource(boots, froststeel) || boots.is(froststeel.getItem());
                    boolean protectedGoo = refused ? boots.is(froststeel.getItem()) : clog;
                    snow(owner, boots, frost);
                    goo(owner, boots, layer, false, true, protectedGoo);
                    goo(owner, boots, layer, true, true, false);
                    goo(owner, boots, layer, false, false, true);
                    goo(owner, boots, full, false, true, false);
                }
                var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                require(zombie != null, "Could not create non-player control");
                goo(zombie, cloggrum, layer, false, true, false);
                goo(owner, stacks.get(3), layer, false, true, false, EquipmentSlot.OFFHAND);
                // A real native Snow Walker root proves that the edited hook retains SG, even after goo refusal.
                var api = new NeoForgeSilentGearTest.NativeApi();
                var sg = api.boot("leather");
                for (var root : paid(owner, sg, new ItemStack(Items.DIAMOND_BOOTS))) { snow(owner, root, true); }
                require(!MODE.equals("chains") || peers > 0, "Competing result chains did not execute");
            }
            REPORT.append("PASS ").append(cases).append(" native-handler/collision cases; mode=").append(MODE)
                    .append("; peer calls=").append(peers).append("\n");
            NetworkAcceptanceFiles.write("undergarden-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.write("undergarden-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("undergarden", failure);
        } finally { server.halt(false); }
    }

    private static FakePlayer player(ServerLevel level) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "UndergardenTest"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(.5, 81, .5); return player;
    }

    private static List<ItemStack> paid(FakePlayer player, ItemStack left, ItemStack right) {
        // This shared paid-menu helper does not initialize the optional SG API.
        return NeoForgeSilentGearTest.workbench(player, left, right);
    }

    private static boolean hasSource(ItemStack root, ItemStack nativeBoot) {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        return data.left().source().is(nativeBoot.getItem()) || data.right().source().is(nativeBoot.getItem());
    }

    private static ItemStack boot(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("undergarden", name + "_boots")).orElseThrow().value());
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("undergarden", name)).orElseThrow().value();
    }

    private static void snow(FakePlayer player, ItemStack input, boolean expected) {
        ItemStack boots = input.copy(), before = boots.copy();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.setItemSlot(EquipmentSlot.FEET, boots); player.setShiftKeyDown(false); player.setPos(.5, 81, .5);
        require(PowderSnowBlock.canEntityWalkOnPowderSnow(player) == expected, "Wrong native snow decision: " + input);
        require(!Blocks.POWDER_SNOW.defaultBlockState().getCollisionShape(player.level(), POS, CollisionContext.of(player)).isEmpty() == expected,
                "Wrong powder-snow surface collision: " + input);
        player.setShiftKeyDown(true);
        require(Blocks.POWDER_SNOW.defaultBlockState().getCollisionShape(player.level(), POS, CollisionContext.of(player)).isEmpty(),
                "Crouching did not preserve native descent");
        player.setShiftKeyDown(false);
        require(ItemStack.matches(before, boots), "Snow query changed root/history");
        cases++;
    }

    private static void goo(LivingEntity entity, ItemStack boots, Block block, boolean gooey, boolean grounded, boolean protectedGoo) throws Exception {
        goo(entity, boots, block, gooey, grounded, protectedGoo, EquipmentSlot.FEET);
    }

    private static void goo(LivingEntity entity, ItemStack input, Block block, boolean gooey, boolean grounded,
                            boolean protectedGoo, EquipmentSlot slot) throws Exception {
        for (var empty : EquipmentSlot.values()) { entity.setItemSlot(empty, ItemStack.EMPTY); }
        ItemStack boots = input.copy(), before = boots.copy();
        entity.setItemSlot(slot, boots); entity.setOnGround(grounded);
        var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("undergarden", "gooey")).orElseThrow();
        entity.removeEffect(effect);
        if (gooey) { require(entity.addEffect(new MobEffectInstance(effect, 100)), "Native Gooey did not apply"); }
        Field stuck = Entity.class.getDeclaredField("stuckSpeedMultiplier"); stuck.setAccessible(true); stuck.set(entity, Vec3.ZERO);
        var contact = block.getClass().getDeclaredMethod("entityInside", BlockState.class, Level.class, BlockPos.class,
                Entity.class, InsideBlockEffectApplier.class, boolean.class);
        contact.setAccessible(true);
        contact.invoke(block, block.defaultBlockState(), entity.level(), POS, entity, InsideBlockEffectApplier.NOOP, false);
        Vec3 expected = protectedGoo ? Vec3.ZERO : block == block("goo") ? new Vec3(.45, .45, .45) : new Vec3(2, 0, 2);
        require(expected.equals(stuck.get(entity)), "Wrong native goo result: " + input + ", gooey=" + gooey + ", slot=" + slot);
        require(ItemStack.matches(before, boots) && entity.getItemBySlot(slot) == boots, "Goo changed root/history or replaced equipment");
        entity.removeEffect(effect);
        cases++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
