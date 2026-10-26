package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Only the real server scheduler ticks these entities. A second JVM reopens their saved world. */
final class NeoForgeJustDireLavaLifecycleTest {
    private static final String PREFIX = "just-dire-lava-lifecycle";
    private static final boolean RESTART = System.getProperty("mismatchedboots.runtimetest.justDireLavaLifecycle").equals("restart");
    private static final BlockPos A = new BlockPos(1605, 80, 5);
    private static final BlockPos B = A.offset(6, 0, 0);
    private static final ChunkPos CHUNK = new ChunkPos(A.getX() >> 4, A.getZ() >> 4);
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static ServerLevel level;
    private static ServerLevel nether;
    private static ItemEntity item;
    private static ItemEntity old;
    private static ItemEntity completed;
    private static ItemEntity first;
    private static ItemEntity second;
    private static ItemEntity coalA;
    private static ItemEntity coalB;
    private static ServerPlayer collector;
    private static ItemStack expected;
    private static UUID unloadedId;
    private static UUID importedWorkingId;
    private static boolean initialized;
    private static boolean started;
    private static boolean finished;
    private static boolean unloaded;
    private static int wait;
    private static int total;

    private record Step(String name, Checked start, BooleanSupplier done, int limit) {
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private NeoForgeJustDireLavaLifecycleTest() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (RESTART && event.getEntity() instanceof ItemEntity entity
                    && entity.getItem().has(JustDireLavaRepairTestSupport.component("floatingticks"))
                    && entity.getItem().has(JustDireLavaRepairTestSupport.component("lavapos"))) {
                importedWorkingId = entity.getUUID();
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() == level && event.getChunk().getPos().equals(CHUNK)) {
                unloaded = true;
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }

        try {
            if (++total > 3_000) {
                throw new AssertionError("Lifecycle total deadline");
            }
            if (!initialized) {
                level = server.overworld();
                level.setChunkForced(CHUNK.x(), CHUNK.z(), true);
                level.getChunk(A);
                if (!level.areEntitiesActuallyLoadedAndTicking(CHUNK)) {
                    return;
                }
                initialized = true;
                if (RESTART) {
                    restart(server);
                } else {
                    save(server);
                }
            }

            Step step = steps.peek();
            if (step == null) {
                // Inject immediately before shutdown so another normal tick cannot sanitize it before disk save.
                if (!RESTART) {
                    set(item.getItem(), "floatingticks", 79);
                    set(item.getItem(), "lavapos", A);
                }
                NetworkAcceptanceFiles.write(prefix() + "-report.txt", report.toString());
                NetworkAcceptanceFiles.write(prefix() + "-success.txt", "neoforge\n");
                finished = true;
                server.halt(false);
                return;
            }
            if (!started) {
                wait = 0;
                started = true;
                step.start.run();
            } else if (step.done.getAsBoolean()) {
                report.append(step.name).append('\n');
                steps.remove();
                started = false;
                NetworkAcceptanceFiles.write(prefix() + "-partial-report.txt", report.toString());
            } else if (++wait > step.limit) {
                throw new AssertionError("Timeout: " + step.name + "; age=" + (item == null ? -1 : item.getAge())
                                                 + "; pos=" + (item == null ? null : item.position()) + "; unloaded=" + unloaded);
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(prefix(), new AssertionError("Step: " + (steps.peek() == null ? "init" : steps.peek().name), failure));
            finished = true;
            server.halt(false);
        }
    }

    private static void save(MinecraftServer server) {
        add("automatic Lava repair of combat-spent FE: one pinned source/receipt, no wallet refund", () -> {
            arena(level);
            level.setBlock(A.east(), Blocks.LAVA.defaultBlockState(), 3);
            expected = root();
            var wearer = NeoForgeJustDireNegateFallTest.player(server, net.minecraft.world.level.GameType.SURVIVAL);
            wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, expected);
            JustDireArmorWearTestSupport.hit(wearer, 12_145, 0);
            wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
            item = drop(level, A, expected.copy());
        }, () -> repaired(item));
        check("successful root continues normal age and pickup delay; no adjacent-source double consumption", () -> {
            require(level.getBlockState(A).is(Blocks.OBSIDIAN) && source(level, A.east()), "Pinned/adjacent receipt mismatch");
            unchanged(item, expected, true);
            require(item.getItem().getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 12_145,
                    "Actual Lava completion refilled combat-spent FE from historical source charge");
        });
        add("healthy completed entity never repeats effects without new contact", () -> {}, () -> wait > 110);
        check("exactly one completion and ordinary age/pickup countdown", () -> {
            require(JustDireLavaRepairTestSupport.sounds.size() == 1 && item.getAge() > 180 && !item.hasPickUpDelay(),
                    "Repeated ritual or skipped vanilla ticking");
            item.discard();
        });
        add("deep source column keeps a bounded pinned anchor", () -> {
            arena(level);
            for (int y = 1; y <= 4; y++) {
                level.setBlock(A.above(y), Blocks.LAVA.defaultBlockState(), 3);
            }
            item = drop(level, A, root());
        }, () -> repaired(item));
        check("deep column consumes bottom anchor only", () -> {
            require(level.getBlockState(A).is(Blocks.OBSIDIAN) && source(level, A.above()), "Deep source retargeted or failed receipt");
            item.discard();
        });
        add("start an advanced episode before source replacement", () -> {
            arena(level);
            item = drop(level, A, root());
        }, () -> item.getAge() >= 70);
        add("source removed during ascent cannot commit remotely", () -> level.setBlock(A, Blocks.AIR.defaultBlockState(), 3), () -> wait > 25);
        check("interrupted source left root damaged and emitted no completion", () -> require(item.getItem().isDamaged()
                                                                                                      && JustDireLavaRepairTestSupport.sounds.isEmpty(),
                                                                                              "Old episode completed after removal"));
        add("new source contact starts a fresh episode, not imported progress", () -> {
            level.setBlock(A, Blocks.LAVA.defaultBlockState(), 3);
            item.setPos(A.getX() + .5, 80.2, 5.5);
            item.setDeltaMovement(Vec3.ZERO);
        }, () -> wait > 35);
        check("fresh contact has not inherited old completion progress",
              () -> require(item.getItem().isDamaged() && source(level, A), "Old progress survived replacement"));
        add("fresh source eventually earns one checked repair", () -> {}, () -> repaired(item));
        check("one fresh receipt after replacement",
              () -> {
                  require(JustDireLavaRepairTestSupport.sounds.size() == 1, "Replacement duplicated completion");
                  item.discard();
              });

        add("prepare real pickup without bypassing the native pickup delay", () -> {
            arena(level);
            item = drop(level, A, root());
        }, () -> item.getAge() >= 20);
        add("disable ritual, wait natural pickup delay while item stays fireproof",
            () -> set(item.getItem(), "lavarepair_toggle", false),
            () -> !item.hasPickUpDelay());
        check("actual playerTouch collects the unchanged damaged stack", () -> {
            expected = item.getItem().copy();
            collector = NeoForgeJustDireNegateFallTest.player(server, net.minecraft.world.level.GameType.SURVIVAL);
            collector.getInventory().clearContent();
            collector.setPos(item.position());
            item.playerTouch(collector);
            require(!item.isAlive() && collector.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ItemStack.matches(stack, expected)),
                    "Native pickup failed");
        });
        add("real player drop owns a new entity and a fresh ritual", () -> {
            var stack = collector.getInventory().removeItemNoUpdate(collector.getInventory().findSlotMatchingItem(expected));
            require(ItemStack.matches(stack, expected), "Pickup inventory stack missing");
            set(stack, "lavarepair_toggle", true);
            collector.setPos(A.getX() + .5, 80.2, 5.5);
            item = collector.drop(stack, false, false);
            require(item != null, "Player drop refused");
            item.setPos(A.getX() + .5, 80.2, 5.5);
            item.setDeltaMovement(Vec3.ZERO);
        }, () -> repaired(item));
        check("pickup/redrop paid only its new source",
              () -> {
                  require(JustDireLavaRepairTestSupport.sounds.size() == 1, "Pickup carried old progress/receipt");
                  item.discard();
              });

        add("spawn actual mixed/vanilla merge and despawn controls outside lava", () -> {
            arena(level);
            level.setBlock(A, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(B, Blocks.STONE.defaultBlockState(), 3);
            first = drop(level, A.above(), root());
            second = drop(level, A.above(), first.getItem().copy());
            coalA = drop(level, B.above(), new ItemStack(Items.COAL));
            coalB = drop(level, B.above(), new ItemStack(Items.COAL));
            item = drop(level, A.above(2), root());
            item.setPos(A.getX() - 1, 82, 5.5);
            var age = ItemEntity.class.getDeclaredField("age");
            age.setAccessible(true);
            age.setInt(item, 5_997);
        }, () -> wait > 80);
        check("normal scheduler merges legal items, retains count-one roots and ages eligible items out", () -> {
            require(!item.isAlive() && first.isAlive() && second.isAlive() && first.getItem().getCount() == 1 && second.getItem().getCount() == 1,
                    "Custom hook skipped despawn or illegally merged nonstackable roots");
            require(coalA.isAlive() != coalB.isAlive() && (coalA.isAlive() ? coalA : coalB).getItem().getCount() == 2,
                    "Positive native merge control did not merge");
            first.discard();
            second.discard();
            coalA.discard();
            coalB.discard();
        });
        add("unforce a real chunk during an episode", () -> {
            arena(level);
            item = drop(level, A, root());
        }, () -> item.getAge() >= 15);
        add("wait actual chunk/entity unload, not a codec clone", () -> {
            old = item;
            unloadedId = item.getUUID();
            expected = item.getItem().copy();
            unloaded = false;
            level.setChunkForced(CHUNK.x(), CHUNK.z(), false);
        }, () -> unloaded && level.getEntity(unloadedId) == null, 900);
        add("load the same chunk/entity UUID again", () -> {
                level.setChunkForced(CHUNK.x(), CHUNK.z(), true);
                level.getChunk(A);
            },
            () -> level.getEntity(unloadedId) instanceof ItemEntity && level.areEntitiesActuallyLoadedAndTicking(CHUNK));
        check("chunk reload has new entity identity and no persisted repair credit", () -> {
            item = (ItemEntity) level.getEntity(unloadedId);
            require(item != old && item.getItem().isDamaged() && source(level, A), "Old episode completed across unload");
            unchanged(item, expected, false);
        });
        add("reloaded root repairs only after fresh physical source contact", () -> {}, () -> repaired(item));
        check("chunk reload has one paid completion",
              () -> {
                  require(JustDireLavaRepairTestSupport.sounds.size() == 1, "Chunk reload duplicated receipt");
                  item.discard();
              });

        add("cross-dimension transfer during ascent", () -> {
            arena(level);
            item = drop(level, A, root());
        }, () -> item.getAge() >= 20);
        add("actual teleport transition creates destination entity away from lava", () -> {
            nether = server.getLevel(Level.NETHER);
            require(nether != null, "Nether unavailable");
            nether.setChunkForced(CHUNK.x(), CHUNK.z(), true);
            arena(nether);
            nether.setBlock(A, Blocks.STONE.defaultBlockState(), 3);
            old = item;
            expected = item.getItem().copy();
            item = (ItemEntity) item.teleport(new TeleportTransition(nether,
                                                                     Vec3.atCenterOf(A.above()),
                                                                     Vec3.ZERO,
                                                                     0,
                                                                     0,
                                                                     TeleportTransition.DO_NOTHING));
            require(item != null && item != old && old.isRemoved(), "No actual dimension replacement");
        }, () -> wait > 110);
        check("dimension transfer has no old-world consumption or remote repair", () -> {
            require(source(level, A) && item.getItem().isDamaged(), "Dimension transition kept old authority");
            unchanged(item, expected, false);
        });
        add("destination fresh contact may begin a new ritual", () -> {
            nether.setBlock(A, Blocks.LAVA.defaultBlockState(), 3);
            item.setPos(A.getX() + .5, 80.2, 5.5);
            item.setDeltaMovement(Vec3.ZERO);
        }, () -> repaired(item));
        check("only destination source paid for repair",
              () -> {
                  require(source(level, A) && nether.getBlockState(A).is(Blocks.OBSIDIAN), "Wrong dimension receipt");
                  item.discard();
              });

        add("prepare completed and mid-ascent actual entities for world restart", () -> {
            arena(level);
            completed = drop(level, B, root());
        }, () -> repaired(completed));
        add("save a second entity while ritual is incomplete", () -> item = drop(level, A, root()), () -> item.getAge() >= 20);
        check("persist world entities and independent full-stack expectations", () -> {
            require(item.getItem().isDamaged() && source(level, A), "No incomplete ritual to persist");
            var json = new JsonObject();
            json.addProperty("partial", item.getUUID().toString());
            json.addProperty("complete", completed.getUUID().toString());
            json.add("partialStack",
                     ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), item.getItem()).getOrThrow());
            json.add("completeStack",
                     ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), completed.getItem())
                                    .getOrThrow());
            NetworkAcceptanceFiles.write(PREFIX + "-saved.json", json.toString());
        });
    }

    private static void restart(MinecraftServer server) throws Exception {
        var saved = JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"),
                                                                    PREFIX + "-saved.json"))).getAsJsonObject();
        var partialId = UUID.fromString(saved.get("partial").getAsString());
        var completeId = UUID.fromString(saved.get("complete").getAsString());
        add("load actual persisted entity UUIDs in a second JVM", () -> JustDireLavaRepairTestSupport.reset(),
            () -> level.getEntity(partialId) instanceof ItemEntity && level.getEntity(completeId) instanceof ItemEntity);
        check("disk stacks retain FE/controls/clocks/source history, not native working progress", () -> {
            item = (ItemEntity) level.getEntity(partialId);
            completed = (ItemEntity) level.getEntity(completeId);
            require(partialId.equals(importedWorkingId), "Stale progress fault did not actually reach disk/entity loading");
            expected = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), saved.get("partialStack"))
                                      .getOrThrow();
            unchanged(item, expected, false);
            var complete = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), saved.get("completeStack"))
                                          .getOrThrow();
            unchanged(completed, complete, false);
            require(!item.getItem().has(JustDireLavaRepairTestSupport.component("floatingticks"))
                            && !item.getItem().has(JustDireLavaRepairTestSupport.component("lavapos")), "Restart trusted native working fields");
        });
        add("partial restart cannot immediately spend saved progress", () -> {}, () -> wait > 20);
        check("no early completion from saved coordinates/79 ticks",
              () -> require(item.getItem().isDamaged() && source(level, A), "Saved progress completed remotely"));
        add("source removal after restart cancels without stale repair",
            () -> level.setBlock(A, Blocks.AIR.defaultBlockState(), 3),
            () -> wait > 100);
        check("completed saved root has no replay and interrupted root stays damaged", () -> require(item.getItem().isDamaged()
                                                                                                             && level.getBlockState(B)
                                                                                                                     .is(Blocks.OBSIDIAN) && JustDireLavaRepairTestSupport.sounds.isEmpty(),
                                                                                                     "Restart replayed repair/effects"));
        add("fresh source contact after restart earns repair", () -> {
            level.setBlock(A, Blocks.LAVA.defaultBlockState(), 3);
            item.setPos(A.getX() + .5, 80.2, 5.5);
            item.setDeltaMovement(Vec3.ZERO);
        }, () -> repaired(item));
        add("ordinary completed ticks do not spend a second source", () -> {}, () -> wait > 100);
        check("one new receipt, exact resource/history preservation after restart", () -> {
            unchanged(item, expected, true);
            require(JustDireLavaRepairTestSupport.sounds.size() == 1 && level.getBlockState(B).is(Blocks.OBSIDIAN), "Restart duplicated completion");
        });
    }

    private static ItemStack root() throws Exception {
        var pair = MixedBootsFactory.createPair(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings",
                                                                                                                              "blazegold_boots"))),
                                                NeoForgeJustDireResourcesTest.boots("eclipsealloy", 24_690));
        var root = pair.primary();
        root.setDamageValue(120);
        set(root, "lavarepair_toggle", true);
        set(root, "lavarepair_bindingmode", 2);
        JustDireEarthquakeTestSupport.bind();
        set(root, "ability_bindings", java.util.List.of(JustDireEarthquakeTestSupport.binding("lavarepair", 86)));
        set(root, "ability_cooldowns", java.util.List.of(JustDireEarthquakeTestSupport.timer("groundstomp", 123, false)));
        return root;
    }

    private static void arena(ServerLevel world) {
        world.getChunk(A);
        for (BlockPos pos : BlockPos.betweenClosed(A.offset(-2, -1, -2), B.offset(2, 9, 2))) {
            world.setBlock(pos, (pos.getY() == 79 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        }
        world.setBlock(A, Blocks.LAVA.defaultBlockState(), 3);
        world.setBlock(B, Blocks.LAVA.defaultBlockState(), 3);
        JustDireLavaRepairTestSupport.reset();
    }

    private static ItemEntity drop(ServerLevel world, BlockPos source, ItemStack stack) {
        var entity = new ItemEntity(world, source.getX() + .5, source.getY() + .2, source.getZ() + .5, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        require(world.addFreshEntity(entity), "Entity spawn refused");
        return entity;
    }

    private static boolean source(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).equals(Blocks.LAVA.defaultBlockState());
    }

    private static boolean repaired(ItemEntity entity) {
        return entity != null && entity.isAlive() && !entity.getItem().isDamaged();
    }

    private static void unchanged(ItemEntity entity, ItemStack before, boolean repair) {
        var allowed = before.copy();
        if (repair) {
            allowed.setDamageValue(0);
        }
        require(ItemStack.matches(allowed, entity.getItem()), "Unexpected root resource/control/history change");
    }

    private static <T> void set(ItemStack stack, String name, T value) {
        stack.set(JustDireLavaRepairTestSupport.component(name), value);
    }

    private static String prefix() {
        return PREFIX + (RESTART ? "-restart" : "-save");
    }

    private static void check(String name, Checked start) {
        add(name, start, () -> true);
    }

    private static void add(String name, Checked start, BooleanSupplier done) {
        add(name, start, done, 400);
    }

    private static void add(String name, Checked start, BooleanSupplier done, int limit) {
        steps.add(new Step(name, start, done, limit));
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
