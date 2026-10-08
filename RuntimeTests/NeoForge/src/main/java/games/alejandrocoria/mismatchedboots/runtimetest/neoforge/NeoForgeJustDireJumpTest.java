package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consolidated gameplay contracts with native originals, not a synthetic event-only oracle. */
public final class NeoForgeJustDireJumpTest {
    private static boolean finished;
    private static int cases;
    private NeoForgeJustDireJumpTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            String guard = System.getProperty("mismatchedboots.runtimetest.justDireJumpGuard", "");
            if (!guard.equals("absent")) {
                Class<?> handler = Class.forName("com.direwolf20.justdirethings.common.events.LivingEntityEvents");
                var ready = handler.getDeclaredMethod("mismatchedboots$justDireJumpReady");
                ready.setAccessible(true);
                require((boolean) ready.invoke(null) == !guard.equals("missingFall"), "Unexpected paired hook permission");
            }
            if (guard.isEmpty()) { matrix(server); }
            else if (guard.equals("absent")) {
                pairs(server, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS), 0);
            } else {
                check(server, boots("ferricore", true), 1);
                pairs(server, boots("blazegold", true), new ItemStack(Items.IRON_BOOTS), guard.equals("compatible") ? 2 : 0);
                if (guard.equals("compatible")) {
                    require(JustDireJumpGuardPlugin.jumpReads > 0 && JustDireJumpGuardPlugin.fallReads > 0,
                            "Competing compatible wrappers were not exercised");
                }
            }
            NetworkAcceptanceFiles.write("just-dire-jump-report.txt", cases + " jump/fall cases; " + (guard.isEmpty()
                    ? "paired production hooks; native controls; maximum/owner settings; provenance; powered exclusion; live wear; "
                        + "unchanged resources; actual health loss; post-mix installation/tier transitions; save/load; native config; "
                        + "water/gliding flags; Feather Falling; AIRBURST/Negate precedence; non-player control"
                    : "guard=" + guard + "; native/mixed movement and actual health; expected paired permission and warning count") + "\n");
            NetworkAcceptanceFiles.write("just-dire-jump-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-jump", failure);
        } finally { server.halt(false); }
    }

    private static void matrix(MinecraftServer server) throws ReflectiveOperationException {
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        ItemStack ferricore = boots("ferricore", true);
        ItemStack blazegold = boots("blazegold", true);
        ItemStack powered = boots("celestigem", true);
        check(server, iron, 0);
        check(server, ferricore, 1);
        check(server, blazegold, 2);
        pairs(server, ferricore, iron, 1);
        pairs(server, blazegold, ferricore, 2);
        pairs(server, ferricore, ferricore, 1);
        pairs(server, ferricore, powered, 1);
        pairs(server, powered, iron, 0);
        pairs(server, boots("eclipsealloy", true), powered, 0);
        pairs(server, boots("ferricore", false), iron, 0);
        ItemStack root = MixedBootsFactory.createPair(blazegold, iron).primary();
        for (String toggle : List.of("tool_enabled", "jumpboost_toggle")) {
            set(root, toggle, false); check(server, root, 0);
            set(root, toggle, true); check(server, root, 2);
        }
        set(root, "jumpboost_value", 999); check(server, root, 2);
        set(root, "jumpboost_value", -99); check(server, root, 1);
        ItemStack one = ferricore.copy(); set(one, "jumpboost_value", 1);
        var owners = MixedBootsFactory.createPair(one, blazegold);
        check(server, owners.primary(), 1); check(server, owners.inverse(), 2);
        ItemStack disabled = blazegold.copy(); set(disabled, "jumpboost_toggle", false);
        var toggles = MixedBootsFactory.createPair(disabled, iron);
        check(server, toggles.primary(), 0); check(server, toggles.inverse(), 2);
        root = MixedBootsFactory.createPair(boots("ferricore", false), iron).primary();
        set(root, "jumpboost_upgrade_installed", true); check(server, root, 0);
        check(server, new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)), 0);
        root = MixedBootsFactory.createPair(ferricore, iron).primary();
        root.setCount(2); check(server, root, 0); root.setCount(1);
        for (int remaining : new int[]{2, 1, 0, 5}) {
            root.setDamageValue(root.getMaxDamage() - remaining);
            check(server, root, remaining > 0 ? 1 : 0);
            ItemStack nativeWear = ferricore.copy();
            nativeWear.setDamageValue(nativeWear.getMaxDamage() - remaining);
            check(server, nativeWear, remaining > 0 ? 1 : 0);
        }
        ItemStack worn = ferricore.copy(); worn.setDamageValue(worn.getMaxDamage());
        root = MixedBootsFactory.createPair(worn, iron).primary();
        root.setDamageValue(0); check(server, root, 1);
        root.setDamageValue(root.getMaxDamage()); check(server, root, 0);
        for (ItemStack stack : List.of(ferricore.copy(), MixedBootsFactory.createPair(ferricore, iron).primary())) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            check(server, stack, 1);
            stack.setDamageValue(stack.getMaxDamage()); check(server, stack, 0);
        }
        root = MixedBootsFactory.createPair(blazegold, iron).primary();
        root.remove(DataComponents.MAX_DAMAGE); check(server, root, 0);
        root = MixedBootsFactory.createPair(blazegold, iron).primary();
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4);
        check(server, root, 2);
        settingsAndTransitions(server, ferricore, blazegold, iron);
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue enabled = flags.entrySet().stream().filter(e -> ((Enum<?>) e.getKey()).name().equals("JUMPBOOST"))
                .map(e -> (ModConfigSpec.BooleanValue) e.getValue()).findFirst().orElseThrow();
        boolean previous = enabled.get();
        try { enabled.set(false); check(server, ferricore, 0); pairs(server, ferricore, iron, 0); }
        finally { enabled.set(previous); }
        for (boolean water : new boolean[]{true, false}) {
            for (ItemStack stack : List.of(blazegold, MixedBootsFactory.createPair(blazegold, iron).primary())) {
                ServerPlayer swimmer = player(server, water, !water);
                swimmer.setDeltaMovement(Vec3.ZERO); swimmer.jumpFromGround();
                double baseline = swimmer.getDeltaMovement().y;
                swimmer.setItemSlot(EquipmentSlot.FEET, stack);
                swimmer.setDeltaMovement(Vec3.ZERO); swimmer.jumpFromGround();
                require(swimmer.getDeltaMovement().y == baseline, "Water/gliding did not suppress additional jump");
                require(fall(swimmer, 9) == fall(player(server), 7), "Water/gliding wrongly suppressed fall companion");
            }
        }
        ItemStack negate = boots("celestigem", true);
        set(negate, "negatefalldamage_upgrade_installed", true); set(negate, "forge_energy", 1000);
        ServerPlayer protectedPlayer = player(server);
        protectedPlayer.setItemSlot(EquipmentSlot.FEET, negate);
        require(fall(protectedPlayer, 9) == 0, "Native powered Negate precedence changed");
        require(component(negate, "forge_energy", 0) == 550, "Native Negate payment changed");
        Pig pig = new Pig(EntityType.PIG, server.overworld());
        pig.jumpFromGround(); double pigControl = pig.getDeltaMovement().y;
        pig.setDeltaMovement(Vec3.ZERO);
        pig.setItemSlot(EquipmentSlot.FEET, MixedBootsFactory.createPair(blazegold, iron).primary());
        pig.jumpFromGround(); require(pig.getDeltaMovement().y == pigControl, "Non-player acquired a passive player ability");
        // Fall handlers must preserve the native hand early return even when a mixed source could jump.
        ServerPlayer player = player(server);
        player.setItemSlot(EquipmentSlot.FEET, MixedBootsFactory.createPair(blazegold, iron).primary());
        for (EquipmentSlot hand : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            player.setItemSlot(hand, new ItemStack(item("ferricore_sword")));
            require(fall(player, 9) == 0, "Native hand AIRBURST early return was lost");
            player.setItemSlot(hand, ItemStack.EMPTY);
        }
    }

    private static void settingsAndTransitions(MinecraftServer server, ItemStack ferricore, ItemStack blazegold, ItemStack iron) {
        ItemStack root = MixedBootsFactory.createPair(boots("ferricore", false), iron).primary();
        ServerPlayer smith = player(server);
        SmithingMenu menu = new SmithingMenu(1, smith.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(item("upgrade_jumpboost")));
        root = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        require(!root.isEmpty(), "Post-mix installation failed"); check(server, root, 1);
        root = MixedBootsRecomposer.recompose(root, blazegold, iron); check(server, root, 2);
        set(root, "jumpboost_value", 1); root.setDamageValue(19);
        root = MixedBootsRecomposer.recompose(root, ferricore, iron); check(server, root, 1);
        require(root.getDamageValue() == 19, "Tier change lost live wear");
        root = MixedBootsRecomposer.recompose(root, blazegold, iron); check(server, root, 1);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        root = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
        require(component(root, "jumpboost_value", 0) == 1 && root.getDamageValue() == 19, "Save/load lost root controls/wear");
        check(server, root, 1);
        root = MixedBootsRecomposer.recompose(root, boots("celestigem", true), iron); check(server, root, 0);
    }

    private static void pairs(MinecraftServer server, ItemStack left, ItemStack right, int value) {
        var pair = MixedBootsFactory.createPair(left, right);
        check(server, pair.primary(), value); check(server, pair.inverse(), value);
    }

    private static void check(MinecraftServer server, ItemStack boots, int value) {
        ItemStack before = boots.copy();
        ServerPlayer player = player(server);
        player.jumpFromGround();
        double vanilla = player.getDeltaMovement().y;
        player.setDeltaMovement(Vec3.ZERO);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.jumpFromGround();
        require(Math.abs(player.getDeltaMovement().y - vanilla - value / 7.5f) < 1e-7,
                "Wrong jump delta: " + boots.getItem() + " expected=" + value + " actual=" + (player.getDeltaMovement().y - vanilla));
        for (float distance : new float[]{2.5f, 4.5f, 9}) {
            float loss = fall(player, distance);
            ServerPlayer control = player(server);
            ItemStack controlBoots = new ItemStack(Items.IRON_BOOTS);
            if (boots.has(DataComponents.ENCHANTMENTS)) { controlBoots.set(DataComponents.ENCHANTMENTS, boots.get(DataComponents.ENCHANTMENTS)); }
            control.setItemSlot(EquipmentSlot.FEET, controlBoots);
            float expected = fall(control, distance - value);
            require(loss == expected, "Wrong actual fall health: distance=" + distance + " value=" + value + " loss=" + loss + " expected=" + expected);
        }
        require(ItemStack.matches(before, boots), "Passive query spent resources or mutated root/snapshots");
        cases++;
    }

    private static float fall(ServerPlayer player, float distance) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.causeFallDamage(distance, 1, player.damageSources().fall());
        return player.getMaxHealth() - player.getHealth();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return player(server, false, false);
    }

    private static ServerPlayer player(MinecraftServer server, boolean water, boolean flying) {
        ServerPlayer player = new ServerPlayer(server, server.overworld(),
                new GameProfile(UUID.randomUUID(), "jump-test"), ClientInformation.createDefault()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isInWater() { return water; }
            @Override public boolean isFallFlying() { return flying; }
        };
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    @SuppressWarnings("unchecked")
    private static <T> T component(ItemStack stack, String path, T fallback) {
        return stack.getOrDefault((DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getValue(Identifier.fromNamespaceAndPath("justdirethings", path)), fallback);
    }

    static ItemStack boots(String tier, boolean installed) {
        ItemStack result = new ItemStack(item(tier + "_boots"));
        if (installed) { set(result, "jumpboost_upgrade_installed", true); }
        return result;
    }

    private static net.minecraft.world.item.Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", path));
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(ItemStack stack, String path, T value) {
        var type = (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getValue(Identifier.fromNamespaceAndPath("justdirethings", path));
        require(type != null, "Missing native component: " + path);
        stack.set(type, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
