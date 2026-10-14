package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Disposable Lava-only candidate. No production adapter or persistent ritual component. */
public final class JustDireLavaRepairCandidate {
    public static BlockPos refusedBlock;
    public static int refusedWrites;
    static boolean nativeReuse;
    static int completions, cancellations;
    private static final Map<ItemEntity, Episode> episodes = new WeakHashMap<>();
    private static Method hasAbility, nativeRepair;
    private static Object lavaAbility;

    private static final class Episode {
        final ItemStack stack;
        final ServerLevel level;
        final BlockPos source;
        int age, progress;
        boolean ended;
        Episode(ItemStack stack, ServerLevel level, BlockPos source) {
            this.stack = stack; this.level = level; this.source = source.immutable();
        }
    }

    private JustDireLavaRepairCandidate() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void initialize() throws ReflectiveOperationException {
        Class<? extends Enum> ability = (Class<? extends Enum>) Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        lavaAbility = Enum.valueOf(ability, "LAVAREPAIR");
        hasAbility = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool").getMethod("hasAbility", ability);
        nativeRepair = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Helpers")
                .getMethod("doLavaRepair", ItemStack.class, ItemEntity.class);
    }

    public static boolean eligible(ItemStack stack) {
        var data = ModContent.getMixedBootsData(stack).orElse(null);
        if (stack.getCount() != 1 || data == null) { return false; }
        for (var half : java.util.List.of(data.left(), data.right())) {
            var source = half.source();
            if (!BuiltInRegistries.ITEM.getKey(source.getItem()).equals(Identifier.fromNamespaceAndPath("justdirethings", "blazegold_boots"))) { continue; }
            try { if ((boolean) hasAbility.invoke(source.getItem(), lavaAbility)) { return true; } }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        return false;
    }

    public static void tick(ItemStack stack, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) { return; }
        if (nativeReuse) { invokeNative(stack, entity); return; }
        // Imported native coordinates never constitute permission to change a world block.
        stack.remove(component("floatingticks")); stack.remove(component("lavapos"));
        var episode = episodes.get(entity);
        boolean enabled = eligible(stack) && stack.isDamaged()
                && !Boolean.FALSE.equals(stack.get(component("tool_enabled")))
                && !Boolean.FALSE.equals(stack.get(component("lavarepair_toggle")));
        if (episode != null && (episode.stack != stack || episode.level != level || !enabled
                || !source(level, episode.source) || Math.abs(entity.getX() - episode.source.getX() - .5) > 2
                || Math.abs(entity.getZ() - episode.source.getZ() - .5) > 2
                || Math.abs(entity.getY() - episode.source.getY()) > 6)) {
            episodes.remove(entity); entity.setOnGround(false); cancellations++; episode = null;
        }
        if (!enabled) { return; }
        if (episode == null) {
            if (!source(level, entity.blockPosition())) { return; }
            episode = new Episode(stack, level, entity.blockPosition()); episodes.put(entity, episode);
            entity.setPickUpDelay(85);
        }
        if (episode.ended) {
            if (!entity.blockPosition().equals(episode.source)) { episodes.remove(entity); }
            return;
        }
        if (++episode.age > 200) { episode.ended = true; entity.setOnGround(false); cancellations++; return; }
        if (entity.blockPosition().equals(episode.source)) { episode.progress = 0; }
        entity.setDeltaMovement(new Vec3(0, entity.getY() - episode.source.getY() < 3 ? .05 : .005, 0));
        entity.move(MoverType.SELF, entity.getDeltaMovement()); entity.setOnGround(true);
        if (++episode.progress < 80) { return; }
        // World mutation is the receipt; only an actual successful consumption earns repair.
        if (source(level, episode.source) && level.setBlock(episode.source, Blocks.OBSIDIAN.defaultBlockState(), 3)) {
            stack.setDamageValue(0); completions++;
        }
        episode.ended = true; entity.setOnGround(false);
    }

    static boolean source(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.LAVA) && state.getValue(LiquidBlock.LEVEL) == 0;
    }

    static boolean invokeNative(ItemStack stack, ItemEntity entity) {
        try { return (boolean) nativeRepair.invoke(null, stack, entity); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    static void reset() { episodes.clear(); nativeReuse = false; refusedBlock = null; refusedWrites = 0; completions = 0; cancellations = 0; }
    static boolean pending(ItemEntity entity) { return episodes.containsKey(entity); }

    @SuppressWarnings("unchecked")
    static <T> DataComponentType<T> component(String name) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.fromNamespaceAndPath("justdirethings", name));
    }
}
