package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.paidPair;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.snapshot;

/** Native contact bodies on real server ItemEntities/players; no replacement damage, event or expiry engine. */
public final class AetherAlkahestTest {
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.aetherAlkahest", "");
    private static final StringBuilder REPORT = new StringBuilder("Alkahest production; mode=" + MODE + "\n");
    private static final BlockPos CONTACT = new BlockPos(0, 80, 0);
    private static final TagKey<Item> LOOT = TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:unbreakable_loot"));
    private static final TagKey<Item> RESISTANCE = TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:alkahest_resistant_item"));
    private static boolean finished;
    private static TagReload reload;
    private static Object fluid;
    private static BlockState fluidState;
    private static Method inside;
    private static FakePlayer observed;
    private static final List<String> armorEvents = new ArrayList<>();

    private AetherAlkahestTest() {}

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
        if (finished) { return; }
        try {
            if (reload != null) {
                if (!reload.advance(server)) { return; }
            } else if (MODE.equals("absent")) {
                require(!ModList.get().isLoaded("aether_ii"), "Absent control contains Aether");
                var pair = paidPair(player(server.overworld()), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
                require(pair.stream().noneMatch(root -> root.is(RESISTANCE)), "Dormant tag projected resistance without a qualifying source");
                REPORT.append("Quiet absence: ordinary paid Workbench pair, no native target requested\n");
            } else {
                fluidState = BuiltInRegistries.BLOCK.getValue(Identifier.parse("aether_ii:alkahest")).defaultBlockState();
                fluid = fluidState.getFluidState().getType();
                inside = fluid.getClass().getMethod("entityInside", BlockState.class, ServerLevel.class, BlockPos.class, Entity.class);
                if (MODE.equals("live")) { matrix(server); reload = new TagReload(server); return; }
                focused(server);
            }
            NetworkAcceptanceFiles.write("aether-alkahest-success.txt", "neoforge\n");
            finished = true;
        } catch (Throwable failure) {
            finished = true;
            NetworkAcceptanceFiles.failure("aether-alkahest", failure);
        } finally {
            if (finished) {
                NetworkAcceptanceFiles.write("aether-alkahest-report.txt", REPORT.toString());
                server.halt(false);
            }
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
                // Boolean protection must not fabricate the inverse owner's upgrade metadata.
                require(root.has(component("reinforcement_tier")) == (profile.tier() && index == 0), "Owner tier policy changed");
                verify(server, profile.name() + "/mixed-" + index, root,
                        profile.tag(), profile.tier());
            }
            require(before.equals(snapshot(server, original)), "Measurements changed original input/defaults");
        }
        var unequal = paidPair(owner, reinforced(owner, api, "zanite", 1), reinforced(owner, api, "arkenium", 2));
        for (int index = 0; index < unequal.size(); index++) {
            require(unequal.get(index).get(component("reinforcement_tier"))
                    == Class.forName("com.aetherteam.aetherii.item.components.ReinforcementTier").getField(index == 0 ? "FIRST" : "SECOND").get(null), "Unequal native tiers were shared/normalized");
            verify(server, "unequal-I-II/" + index, unequal.get(index), true, true);
        }
        var inverse = paidPair(owner, reinforced(owner, api, "beast_pelt", 1), new ItemStack(Items.DIAMOND_BOOTS)).get(1);
        var missing = inverse.copy(); missing.remove(ModContent.mixedBootsDataType().orElseThrow());
        verify(server, "invalid/missing", missing, false, false);
        var countTwo = inverse.copy(); countTwo.setCount(2);
        dropped(server, "invalid/count-two", countTwo, false);
        var spoof = new ItemStack(Items.DIAMOND_BOOTS);
        spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(inverse).orElseThrow());
        verify(server, "invalid/foreign-item", spoof, false, false);
        // Exercise strict admission with a genuinely tagged source, not only a tiered one.
        var tagged = paidPair(owner, item("arkenium_boots"), new ItemStack(Items.DIAMOND_BOOTS)).get(1);
        var bareTagged = tagged.copy(); bareTagged.remove(ModContent.mixedBootsDataType().orElseThrow());
        verify(server, "invalid/tag-missing", bareTagged, false, false);
        var taggedCount = tagged.copy(); taggedCount.setCount(2);
        require(!taggedCount.is(RESISTANCE), "Count-two root gained tag protection");
        dropped(server, "invalid/tag-count-two", taggedCount, false);
        var taggedSpoof = new ItemStack(Items.DIAMOND_BOOTS);
        taggedSpoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(tagged).orElseThrow());
        verify(server, "invalid/tag-foreign-item", taggedSpoof, false, false);
        REPORT.append("All five paid native reinforcement paths, all seven base boots; owner/inverse; unequal tiers; malformed/foreign controls; full state/history retained apart from measured native wear\n");
    }

    private static void verify(MinecraftServer server, String label, ItemStack input, boolean tag, boolean tier) throws Exception {
        verify(server, label, input, tag, tier, tier);
    }

    private static void verify(MinecraftServer server, String label, ItemStack input, boolean tag, boolean fluidTier, boolean armorTier) throws Exception {
        boolean itemProtection = tag || fluidTier;
        require(input.is(RESISTANCE) == tag, "Wrong production resistance tag: " + label);
        if (ModContent.isMixedBoots(input)) { require(!input.is(LOOT), "Mixed root inherited broken-loot lifecycle"); }
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
        require(first == 1 && second == (armorTier ? 0 : 1), "Unexpected native armor timing: " + label + " first=" + first + " next=" + second + " events=" + armorEvents);
        require(armorEvents.size() == 2 && subject.getHealth() < subject.getMaxHealth(), "Worn path did not traverse actual damage/events");
        var attacker = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(attacker != null, "Missing physical damage control");
        subject.invulnerableTime = 0;
        try { subject.hurtServer(server.overworld(), subject.damageSources().mobAttack(attacker), 4); }
        finally { attacker.discard(); }
        int afterPhysical = worn.getDamageValue() - first - second;
        require(afterPhysical == (armorTier ? 0 : 1), "Native last-source carryover changed: " + label + " wear=" + afterPhysical + " events=" + armorEvents);
        unchangedExceptWear(server, input, worn); observed = null;
        REPORT.append("CASE ").append(label).append(" tag=").append(tag).append(" fluid-tier=").append(fluidTier).append(" armor-tier=").append(armorTier)
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

    private static void focused(MinecraftServer server) throws Exception {
        var owner = player(server.overworld());
        var nativeBoots = reinforced(owner, new AetherIICompatibilityTest.NativeApi(), "beast_pelt", 1);
        var roots = paidPair(owner, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
        verify(server, "native-positive", nativeBoots, false, true);
        verify(server, "root-native-positive", roots.getFirst(), false, true);
        boolean fluid = !MODE.startsWith("fluid"), armor = !MODE.startsWith("armor");
        for (int repeat = 0; repeat < 3; repeat++) {
            require(!roots.get(1).has(component("reinforcement_tier")), "Inverse unexpectedly has root tier");
            verify(server, "inverse/" + repeat, roots.get(1), false, fluid, armor);
        }
        var tagged = paidPair(owner, item("arkenium_boots"), new ItemStack(Items.DIAMOND_BOOTS));
        for (int index = 0; index < tagged.size(); index++) { verify(server, "tag-only/" + index, tagged.get(index), true, false); }
        verify(server, "vanilla-negative", new ItemStack(Items.DIAMOND_BOOTS), false, false);
        if (MODE.equals("chains")) {
            require(AetherAlkahestTestControl.observations > 0, "Real peer chains were not exercised");
        }
        if (MODE.endsWith("binding")) { require(AetherAlkahestTestControl.bindingFailures == 1, "Optional failure retried instead of latching once"); }
        REPORT.append("Independent production domains/native positives/tag isolation/history: passed; peer observations=")
                .append(AetherAlkahestTestControl.observations).append("; binding failures=").append(AetherAlkahestTestControl.bindingFailures).append('\n');
    }

    /** Real asynchronous tag replacement/restoration on the same already-paid roots. */
    private static final class TagReload {
        private static final String PACK = "file/mismatchedboots-alkahest-tag-reload";
        private final List<Profile> profiles = new ArrayList<>();
        private final List<String> snapshots;
        private CompletableFuture<Void> future;
        private boolean restoring;

        private TagReload(MinecraftServer server) throws Exception {
            var owner = player(server.overworld());
            var arkenium = item("arkenium_boots"); var diamond = new ItemStack(Items.DIAMOND_BOOTS);
            profiles.add(new Profile("arkenium", arkenium, true, false));
            profiles.add(new Profile("diamond", diamond, false, false));
            for (var original : List.of(arkenium, diamond)) {
                var pair = paidPair(owner, original, new ItemStack(Items.IRON_BOOTS));
                for (int index = 0; index < pair.size(); index++) {
                    profiles.add(new Profile((original == arkenium ? "arkenium" : "diamond") + "/mixed-" + index,
                            pair.get(index), original == arkenium, false));
                }
            }
            for (var profile : profiles) { require(profile.boots().is(RESISTANCE) == profile.tag(), "Wrong pre-reload membership"); }
            snapshots = profiles.stream().map(profile -> snapshot(server, profile.boots())).toList();
            var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mismatchedboots-alkahest-tag-reload");
            Files.createDirectories(pack); var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            Files.writeString(pack.resolve("pack.mcmeta"), """
                    {"pack":{"description":"Fixture-only Alkahest tag reload","min_format":[%d,%d],"max_format":[%d,%d]}}
                    """.formatted(format.major(), format.minor(), format.major(), format.minor()));
            var tag = pack.resolve("data/aether_ii/tags/item/alkahest_resistant_item.json");
            Files.createDirectories(tag.getParent());
            Files.writeString(tag, "{\"replace\":true,\"values\":[\"minecraft:diamond_boots\"]}");
            server.getPackRepository().reload();
            require(server.getPackRepository().isAvailable(PACK), "Alkahest tag datapack not discovered");
            var selected = new ArrayList<>(server.getPackRepository().getSelectedIds()); selected.add(PACK);
            future = server.reloadResources(selected);
        }

        private boolean advance(MinecraftServer server) throws Exception {
            if (!future.isDone()) { return false; } future.join();
            for (int index = 0; index < profiles.size(); index++) {
                var profile = profiles.get(index);
                verify(server, "reload/" + (restoring ? "restored/" : "replaced/") + profile.name(),
                        profile.boots(), restoring == profile.tag(), false);
                require(snapshots.get(index).equals(snapshot(server, profile.boots())), "Tag reload changed existing root/history");
            }
            if (!restoring) {
                restoring = true;
                var selected = new ArrayList<>(server.getPackRepository().getSelectedIds()); selected.remove(PACK);
                future = server.reloadResources(selected); return false;
            }
            REPORT.append("PRODUCTION TAG RELOAD: two completed asynchronous reloads replace/restore source membership; same paid inverses gain/lose dropped and both-hand protection, never tier armor protection; full histories unchanged\n");
            return true;
        }
    }

    private static FakePlayer player(ServerLevel level) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AlkahestTest"));
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
