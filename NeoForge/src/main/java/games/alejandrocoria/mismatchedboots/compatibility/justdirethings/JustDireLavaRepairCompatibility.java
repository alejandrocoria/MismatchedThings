package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Intrinsic item protection and one transient, checked root ritual; native particle animation stays in JDT. */
public final class JustDireLavaRepairCompatibility {
    private static volatile Api api;
    private static volatile Visuals visuals;
    // Each map belongs to its logical thread; prediction never shares server mutation authority.
    // Values must not strongly retain their entity through its stack.
    private static final Map<ItemEntity, Episode> episodes = new WeakHashMap<>();
    private static final Map<ItemEntity, Episode> clientEpisodes = new WeakHashMap<>();

    private JustDireLavaRepairCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireLavaRepairCompatibility::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            Item provider = BuiltInRegistries.ITEM.get(id("blazegold_boots")).orElseThrow().value();
            var query = MethodHandles.publicLookup().findVirtual(tool, "hasAbility", MethodType.methodType(boolean.class, ability))
                    .bindTo(provider).bindTo(ability.getField("LAVAREPAIR").get(null));
            api = new Api(provider, query, bool("tool_enabled"), bool("lavarepair_toggle"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things Lava Repair API; mixed Lava Repair disabled", failure);
        }
        try {
            var data = Class.forName("com.direwolf20.justdirethings.client.particles.itemparticle.ItemFlowParticleData");
            var constructor = MethodHandles.publicLookup().findConstructor(data, MethodType.methodType(void.class,
                    ItemStack.class, double.class, double.class, double.class, int.class))
                    .asType(MethodType.methodType(ParticleOptions.class, ItemStack.class, double.class, double.class, double.class, int.class));
            var holder = (Supplier<?>) Class.forName("com.direwolf20.justdirethings.setup.JDTRegistration").getField("GooBlock_Tier2").get(null);
            Item visualItem = ((ItemLike) holder.get()).asItem();
            visuals = new Visuals(constructor, visualItem);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things Lava Repair particles; mixed Lava Repair particles disabled", failure);
        }
    }

    /** Permanent intrinsic capability, independent of wear, FE, root switches and visual readiness. */
    public static boolean eligible(ItemStack root) {
        Api binding = api;
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (binding == null || !(root.getItem() instanceof MixedBootsItem) || root.getCount() != 1 || data == null) { return false; }
        if (!BootHalf.isValidSource(data.left().source()) || !BootHalf.isValidSource(data.right().source())) { return false; }
        if (data.left().source().getItem() != binding.provider && data.right().source().getItem() != binding.provider) { return false; }
        try { return (boolean) binding.enabledAbility.invokeExact() && api == binding; }
        catch (Throwable failure) {
            fatal(failure);
            synchronized (JustDireLavaRepairCompatibility.class) {
                if (api == binding) {
                    api = null;
                    Constants.LOG.warn("Just Dire Things Lava Repair query failed; mixed Lava Repair disabled", failure);
                }
            }
            return false;
        }
    }

    public static void tick(ItemStack root, ItemEntity entity) {
        Level level = entity.level();
        boolean client = level.isClientSide();
        if (!client) { sanitizeProgress(root); }
        Api binding = api;
        boolean enabled = eligible(root) && binding != null
                && root.getOrDefault(binding.toolEnabled, true) && root.getOrDefault(binding.toggle, true);
        Map<ItemEntity, Episode> localEpisodes = client ? clientEpisodes : episodes;
        Episode episode = localEpisodes.get(entity);
        if (episode != null && (!enabled || !episode.owns(entity, root)
                || !source(level, episode.source) || !episode.inBounds(entity))) {
            localEpisodes.remove(entity); entity.setOnGround(false); episode = null;
        }
        if (!enabled) { return; }
        if (episode == null) {
            BlockPos anchor = source(level, entity.blockPosition()) ? entity.blockPosition()
                    : client ? clientAnchor(entity) : null;
            if (anchor == null) { return; }
            episode = new Episode(root, level, anchor);
            localEpisodes.put(entity, episode); entity.setPickUpDelay(85);
        }
        if (episode.ended) {
            // A timed-out airborne prediction must not recover itself from its own last rise velocity.
            if (!client && !entity.blockPosition().equals(episode.source)) { localEpisodes.remove(entity); }
            return;
        }
        if (++episode.age > 200) { episode.ended = true; entity.setOnGround(false); return; }
        if (entity.blockPosition().equals(episode.source)) { episode.progress = 0; entity.setPickUpDelay(85); }
        rise(entity, episode.source);
        // Client prediction is kinematics only: vanilla item physics otherwise falls between server updates.
        // End on authoritative source/stack changes or timeout, not an independent client completion clock.
        if (client) { return; }
        ServerLevel server = (ServerLevel) level;
        if (++episode.progress >= 80) {
            episode.ended = true; entity.setOnGround(false);
            // JDT's full helper repairs even after a refused block write. Only this receipt earns repair;
            // mark the episode ended before notifications, and never catch/retry committed world mutation.
            if (source(server, episode.source) && server.setBlock(episode.source, Blocks.OBSIDIAN.defaultBlockState(), 3)
                    && episode.owns(entity, root)) {
                root.setDamageValue(0);
                // In-place component edits alone do not dirty ItemEntity's synchronized stack.
                entity.setItem(root.copy());
                server.playSound(null, episode.source, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1, 1);
            }
        } else if (episode.progress < 40) { particles(server, entity, episode.source); }
    }

    private static void rise(ItemEntity entity, BlockPos source) {
        entity.setDeltaMovement(new Vec3(0, entity.getY() - source.getY() < 3 ? .05 : .005, 0));
        entity.move(MoverType.SELF, entity.getDeltaMovement()); entity.setOnGround(true);
    }

    private static BlockPos clientAnchor(ItemEntity entity) {
        // A newly tracked item may already be above lava. Recover only presentation from the
        // synchronized native rise velocity after vanilla gravity/drag (including wire quantization),
        // never a server block receipt. The fast .05 rise becomes .01 in air before drag.
        double vy = entity.getDeltaMovement().y;
        if (Math.abs(vy - .01) > .001 && Math.abs(vy - .049) > .001 && Math.abs(vy - .0049) > .001) { return null; }
        BlockPos pos = entity.blockPosition();
        for (int i = 0; i < 6; i++) {
            pos = pos.below();
            if (!entity.level().hasChunkAt(pos)) { return null; }
            if (source(entity.level(), pos)) { return pos; }
            var state = entity.level().getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.LAVA)) { return null; }
        }
        return null;
    }

    private static void particles(ServerLevel level, ItemEntity entity, BlockPos source) {
        Visuals binding = visuals;
        if (binding == null) { return; }
        try {
            // Reuse JDT's public particle options, codec and client factory, not the unsafe ritual helper.
            var options = (ParticleOptions) binding.create.invokeExact(new ItemStack(binding.item),
                    entity.getX(), entity.getY() + .75, entity.getZ(), 3);
            for (int i = 0; i < 5; i++) {
                level.sendParticles(options, source.getX() + level.getRandom().nextDouble(), source.getY() + .95,
                        source.getZ() + level.getRandom().nextDouble(), 1, 0, 0, 0, 0);
            }
        } catch (Throwable failure) {
            fatal(failure);
            synchronized (JustDireLavaRepairCompatibility.class) {
                if (visuals == binding) {
                    visuals = null;
                    Constants.LOG.warn("Just Dire Things Lava Repair particle emission failed; mixed Lava Repair particles disabled", failure);
                }
            }
        }
    }

    private static boolean source(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) { return false; }
        var state = level.getBlockState(pos);
        return state.is(Blocks.LAVA) && state.getValue(LiquidBlock.LEVEL) == 0;
    }

    /** Registry-only cleanup: native source history is retained, but copied coordinates never authorize repair. */
    public static void sanitizeProgress(ItemStack root) {
        for (String path : List.of("floatingticks", "lavapos")) {
            BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).ifPresent(holder -> root.remove(holder.value()));
        }
    }

    public static void clearEpisodes() { episodes.clear(); }
    public static void clearClientEpisodes() { clientEpisodes.clear(); }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Boolean> bool(String path) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).orElseThrow().value();
        if (type.codec() == null || !(type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(false)).getOrThrow() instanceof Boolean)) {
            throw new IllegalStateException("Unsupported Lava Repair control " + path);
        }
        return (DataComponentType<Boolean>) type;
    }

    private static void fatal(Throwable failure) {
        if (failure instanceof Error error && !(error instanceof LinkageError)) { throw error; }
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("justdirethings", path); }

    private record Api(Item provider, MethodHandle enabledAbility, DataComponentType<Boolean> toolEnabled, DataComponentType<Boolean> toggle) {}
    private record Visuals(MethodHandle create, Item item) {}
    private static final class Episode {
        final WeakReference<ItemStack> stack;
        final Level level;
        final MixedBootsData data;
        final BlockPos source;
        int age, progress;
        boolean ended;
        Episode(ItemStack root, Level level, BlockPos source) {
            this.stack = new WeakReference<>(root); this.level = level;
            this.data = ModContent.getMixedBootsData(root).orElseThrow(); this.source = source.immutable();
        }
        boolean owns(ItemEntity entity, ItemStack root) {
            return entity.isAlive() && entity.level() == level && entity.getItem() == root && stack.get() == root
                    && root.getCount() == 1 && data.equals(ModContent.getMixedBootsData(root).orElse(null));
        }
        boolean inBounds(ItemEntity entity) {
            return Math.abs(entity.getX() - source.getX() - .5) <= 2 && Math.abs(entity.getZ() - source.getZ() - .5) <= 2
                    && Math.abs(entity.getY() - source.getY()) <= 6;
        }
    }
}
