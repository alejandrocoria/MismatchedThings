package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.paidPair;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.snapshot;

/** Native contact bodies on real server ItemEntities/players; no replacement damage, event or expiry engine. */
public final class AetherAlkahestSpike {
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.aetherAlkahest", "");
    private static final boolean CANDIDATE = MODE.equals("candidate");
    private static final StringBuilder REPORT = new StringBuilder("Alkahest research; mode=" + MODE + "\n");
    private static final BlockPos CONTACT = new BlockPos(0, 80, 0);
    private static final TagKey<Item> LOOT = TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:unbreakable_loot"));
    private static boolean finished;
    private static Object fluid;
    private static BlockState fluidState;
    private static Method inside;
    private static FakePlayer observed;
    private static final List<String> armorEvents = new ArrayList<>();

    private AetherAlkahestSpike() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() == observed && !event.getArmorItemStack(EquipmentSlot.FEET).isEmpty()) {
                var last = observed.getLastDamageSource();
                armorEvents.add("last=" + (last == null ? "none" : last.getMsgId())
                        + ",current=" + event.getDamageSource().getMsgId() + ",wear=" + event.getNewDamage(EquipmentSlot.FEET));
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; } finished = true;
        try {
            if (MODE.equals("absent")) {
                require(!ModList.get().isLoaded("aether_ii"), "Absent control contains Aether");
                var pair = paidPair(player(server.overworld()), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
                require(pair.stream().noneMatch(root -> root.is(AetherAlkahestSpikeControl.RESISTANCE)), "Absent candidate projected resistance");
                REPORT.append("Quiet absence: ordinary paid Workbench pair, no native target requested\n");
            } else {
                fluidState = BuiltInRegistries.BLOCK.getValue(Identifier.parse("aether_ii:alkahest")).defaultBlockState();
                fluid = fluidState.getFluidState().getType();
                inside = fluid.getClass().getMethod("entityInside", BlockState.class, ServerLevel.class, BlockPos.class, Entity.class);
                matrix(server);
                if (CANDIDATE) {
                    require(AetherAlkahestSpikeControl.fluidExtensions > 0 && AetherAlkahestSpikeControl.armorExtensions > 0
                            && AetherAlkahestSpikeControl.tagExtensions > 0, "Candidate did not exercise all actual protection seams");
                }
                REPORT.append("SEAMS fluid reads=").append(AetherAlkahestSpikeControl.fluidReads)
                        .append(" extensions=").append(AetherAlkahestSpikeControl.fluidExtensions)
                        .append("; armor reads=").append(AetherAlkahestSpikeControl.armorReads)
                        .append(" extensions=").append(AetherAlkahestSpikeControl.armorExtensions)
                        .append("; tag extensions=").append(AetherAlkahestSpikeControl.tagExtensions).append('\n');
            }
            NetworkAcceptanceFiles.write("aether-alkahest-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("aether-alkahest", failure);
        } finally {
            NetworkAcceptanceFiles.write("aether-alkahest-report.txt", REPORT.toString());
            server.halt(false);
        }
    }

    private record Profile(String name, ItemStack boots, boolean tag, boolean tier) {}

    private static void matrix(MinecraftServer server) throws Exception {
        var owner = player(server.overworld());
        var profiles = new ArrayList<Profile>();
        profiles.add(new Profile("diamond", new ItemStack(Items.DIAMOND_BOOTS), false, false));
        for (var family : List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune", "sentry")) {
            var boots = item(family + "_boots");
            require(!boots.has(component("reinforcement_tier")), "Unexpected native default tier: " + family);
            profiles.add(new Profile(family, boots, family.equals("arkenium"), false));
        }
        var api = new AetherIICompatibilityTest.NativeApi();
        for (var family : List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite")) {
            profiles.add(new Profile(family + "-I", reinforced(owner, api, family, 1), family.equals("arkenium"), true));
        }
        for (var profile : profiles) {
            var original = profile.boots(); String before = snapshot(server, original);
            verify(server, profile.name(), original, profile.tag(), profile.tier());
            var roots = paidPair(owner, original, new ItemStack(Items.DIAMOND_BOOTS));
            for (int index = 0; index < roots.size(); index++) {
                var root = roots.get(index);
                // Keep the actual owner tier different: the candidate must protect, not fabricate upgrade metadata.
                require(root.has(component("reinforcement_tier")) == (profile.tier() && index == 0), "Owner tier policy changed");
                verify(server, profile.name() + "/mixed-" + index, root,
                        CANDIDATE && profile.tag(), profile.tier() && (CANDIDATE || index == 0));
            }
            require(before.equals(snapshot(server, original)), "Measurements changed original input/defaults");
        }
        var unequal = paidPair(owner, reinforced(owner, api, "zanite", 1), reinforced(owner, api, "arkenium", 2));
        for (int index = 0; index < unequal.size(); index++) {
            require(unequal.get(index).get(component("reinforcement_tier"))
                    == Class.forName("com.aetherteam.aetherii.item.components.ReinforcementTier").getField(index == 0 ? "FIRST" : "SECOND").get(null), "Unequal native tiers were shared/normalized");
            verify(server, "unequal-I-II/" + index, unequal.get(index), CANDIDATE, true);
        }
        var inverse = paidPair(owner, reinforced(owner, api, "beast_pelt", 1), new ItemStack(Items.DIAMOND_BOOTS)).get(1);
        var missing = inverse.copy(); missing.remove(ModContent.mixedBootsDataType().orElseThrow());
        verify(server, "invalid/missing", missing, false, false);
        var countTwo = inverse.copy(); countTwo.setCount(2);
        dropped(server, "invalid/count-two", countTwo, false);
        var spoof = new ItemStack(Items.DIAMOND_BOOTS);
        spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(inverse).orElseThrow());
        verify(server, "invalid/foreign-item", spoof, false, false);
        REPORT.append("All five paid native reinforcement paths, all seven base boots; owner/inverse; unequal tiers; malformed/foreign controls; full state/history retained apart from measured native wear\n");
    }

    private static void verify(MinecraftServer server, String label, ItemStack input, boolean tag, boolean tier) throws Exception {
        boolean itemProtection = tag || tier;
        require(input.is(AetherAlkahestSpikeControl.RESISTANCE) == tag, "Wrong native/candidate resistance tag: " + label);
        if (ModContent.isMixedBoots(input)) { require(!input.is(LOOT), "Candidate inherited broken-loot lifecycle"); }
        dropped(server, label, input, itemProtection);
        for (var hand : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            var subject = player(server.overworld()); var stack = input.copy(); subject.setItemSlot(hand, stack);
            subject.tickCount = 20; contact(server, subject); subject.invulnerableTime = 0;
            subject.tickCount = 40; contact(server, subject);
            require(stack.getDamageValue() == (itemProtection ? 0 : 2), "Wrong native hand wear: " + label + "/" + hand + "=" + stack.getDamageValue());
            require(subject.getHealth() < subject.getMaxHealth(), "Protection accidentally grants health immunity");
            unchangedExceptWear(server, input, stack);
        }
        var subject = player(server.overworld()); var worn = input.copy(); subject.setItemSlot(EquipmentSlot.FEET, worn);
        observed = subject; armorEvents.clear();
        subject.tickCount = 20; contact(server, subject); int first = worn.getDamageValue();
        subject.invulnerableTime = 0; subject.tickCount = 40; contact(server, subject); int second = worn.getDamageValue() - first;
        // Measure native timing honestly: the event reads LAST damage, which may not be this contact's source yet.
        require(first == 1 && second == (tier ? 0 : 1), "Unexpected native armor timing: " + label + " first=" + first + " next=" + second + " events=" + armorEvents);
        require(armorEvents.size() == 2 && subject.getHealth() < subject.getMaxHealth(), "Worn path did not traverse actual damage/events");
        var attacker = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(attacker != null, "Missing physical damage control");
        subject.invulnerableTime = 0;
        try { subject.hurtServer(server.overworld(), subject.damageSources().mobAttack(attacker), 4); }
        finally { attacker.discard(); }
        int afterPhysical = worn.getDamageValue() - first - second;
        require(afterPhysical == (tier ? 0 : 1), "Native last-source carryover changed: " + label + " wear=" + afterPhysical + " events=" + armorEvents);
        unchangedExceptWear(server, input, worn); observed = null;
        REPORT.append("CASE ").append(label).append(" tag=").append(tag).append(" tier-protection=").append(tier)
                .append(" drop/hand-protected=").append(itemProtection).append(" worn first/next/physical=")
                .append(first).append('/').append(second).append('/').append(afterPhysical).append("; ").append(armorEvents).append('\n');
    }

    private static void dropped(MinecraftServer server, String label, ItemStack input, boolean protectedItem) throws Exception {
        var entity = new ItemEntity(server.overworld(), 0, 80, 0, input.copy()); entity.setNoGravity(true);
        try {
            entity.lifespan = 6000; contact(server, entity); contact(server, entity);
            require(entity.lifespan == (protectedItem ? 6000 : 5970), "Wrong native dropped lifespan: " + label + "=" + entity.lifespan);
            require(ItemStack.matches(input, entity.getItem()), "Dropped query changed complete item/history");
            // Native ItemEntity.tick owns expiration. Contact is invoked directly; this is not movement/collision automation.
            if (!protectedItem && ModContent.isMixedBoots(input)) {
                entity.lifespan = 50;
                for (int step = 0; step < 10 && !entity.isRemoved(); step++) { contact(server, entity); entity.tick(); }
                require(entity.isRemoved(), "Unprotected root did not naturally expire after native corrosion: " + label);
            }
            if (!protectedItem && input.is(LOOT)) {
                var area = new AABB(-2, 78, -2, 2, 82, 2);
                var prior = server.overworld().getEntitiesOfClass(ItemEntity.class, area).stream().map(Entity::getUUID).toList();
                entity.lifespan = 500; contact(server, entity);
                var outputs = server.overworld().getEntitiesOfClass(ItemEntity.class, area).stream()
                        .filter(value -> !prior.contains(value.getUUID()) && value.getItem().is(item("broken_item").getItem())).toList();
                require(entity.isRemoved() && outputs.size() == 1, "Native loot did not produce one actual broken container: " + label);
                var broken = outputs.getFirst();
                try {
                    var stored = broken.getItem().get(component("broken_stack"));
                    require(stored != null && ItemStack.matches(input, (ItemStack) stored.getClass().getMethod("stack").invoke(stored)), "Native broken loot lost original contents");
                    int life = broken.lifespan; contact(server, broken);
                    require(broken.lifespan == life, "Native broken container lost its own corrosion resistance");
                } finally { broken.discard(); }
                REPORT.append("NATIVE BROKEN ").append(label).append(": actual conversion retains original, resistant wrapper; mixed roots do not inherit loot admission\n");
            }
        } finally { entity.discard(); }
    }

    private static void unchangedExceptWear(MinecraftServer server, ItemStack before, ItemStack after) {
        var normalized = after.copy(); normalized.setDamageValue(before.getDamageValue());
        require(snapshot(server, before).equals(snapshot(server, normalized)), "Native wear changed other root/history data");
    }

    private static FakePlayer player(ServerLevel level) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AlkahestSpike"));
        player.setGameMode(GameType.SURVIVAL); player.setInvulnerable(false); player.setNoGravity(true); player.setPos(0, 80, 0);
        // FakePlayers otherwise retain the native login-load grace period, independently of setInvulnerable(false).
        player.connection.markClientLoaded();
        require(!player.isInvulnerableTo(level, player.damageSources().generic()), "Damage subject still has native spawn/login immunity");
        return player;
    }

    private static void contact(MinecraftServer server, Entity subject) throws Exception {
        inside.invoke(fluid, fluidState, server.overworld(), CONTACT, subject);
    }

    private static ItemStack reinforced(FakePlayer owner, AetherIICompatibilityTest.NativeApi api, String family, int tiers) throws Exception {
        var original = item(family + "_boots"); var menu = api.forge(owner); menu.getSlot(0).set(original.copy());
        var type = Class.forName("com.aetherteam.aetherii.item.components.ReinforcementTier");
        for (int index = 0; index < tiers; index++) {
            Object tier = type.getField(index == 0 ? "FIRST" : "SECOND").get(null); int number = index + 1;
            int primary = (int) type.getMethod("getPrimaryCostForTier", net.minecraft.core.RegistryAccess.class, ItemStack.class, int.class)
                    .invoke(null, owner.registryAccess(), menu.getSlot(0).getItem(), number);
            int secondary = (int) type.getMethod("getSecondaryCostForTier", net.minecraft.core.RegistryAccess.class, ItemStack.class, int.class)
                    .invoke(null, owner.registryAccess(), menu.getSlot(0).getItem(), number);
            var plates = item("arkenium_plate"); plates.setCount(primary); menu.getSlot(1).set(plates);
            var crystals = secondary == 0 ? ItemStack.EMPTY : item("corrobonite_crystal"); if (secondary > 0) { crystals.setCount(secondary); } menu.getSlot(2).set(crystals);
            require((boolean) menu.getClass().getMethod("upgradeItem", net.minecraft.core.RegistryAccess.class, type).invoke(menu, owner.registryAccess(), tier), "Paid reinforcement rejected");
            require(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Reinforcement was not paid");
        }
        return menu.getSlot(0).getItem().copy();
    }
}
