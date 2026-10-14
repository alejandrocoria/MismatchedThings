package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Read-only effect observers and one refused-write fault, not a gameplay implementation. */
public final class JustDireLavaRepairTestSupport {
    public static volatile boolean capture;
    public static BlockPos refusedBlock;
    public static int refusedWrites;
    public static volatile int clientParticles;
    public static final List<Emission> emissions = new ArrayList<>();
    public static final List<BlockPos> sounds = new ArrayList<>();
    public record Emission(ParticleOptions options, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {}

    private JustDireLavaRepairTestSupport() {}
    public static void particle(ParticleOptions options, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (capture && options.getClass().getName().equals("com.direwolf20.justdirethings.client.particles.itemparticle.ItemFlowParticleData")) {
            emissions.add(new Emission(options, x, y, z, count, dx, dy, dz, speed));
        }
    }
    static void reset() { capture = true; refusedBlock = null; refusedWrites = 0; emissions.clear(); sounds.clear(); }

    static void validateEmissions(BlockPos source) throws ReflectiveOperationException {
        require(!emissions.isEmpty() && emissions.size() % 5 == 0, "Native particle density missing");
        for (var emission : emissions) {
            require(emission.x >= source.getX() && emission.x < source.getX() + 1
                    && emission.z >= source.getZ() && emission.z < source.getZ() + 1 && emission.y == source.getY() + .95,
                    "Particles emitted outside actual source");
            require(emission.count == 1 && emission.dx == 0 && emission.dy == 0 && emission.dz == 0 && emission.speed == 0,
                    "Particle spread/speed differs from native emitter");
            var options = emission.options;
            require(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).getNamespace().equals("justdirethings"), "Non-native particle type");
            ItemStack item = (ItemStack) options.getClass().getMethod("getItemStack").invoke(options);
            require(BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().equals("gooblock_tier2"), "Wrong native visual item: " + item);
            require((int) options.getClass().getMethod("getTicksPerBlock").invoke(options) == 3, "Native particle speed changed");
            require((double) options.getClass().getMethod("getTargetX").invoke(options) == source.getX() + .5
                    && (double) options.getClass().getMethod("getTargetZ").invoke(options) == source.getZ() + .5
                    && (double) options.getClass().getMethod("getTargetY").invoke(options) > source.getY() + .75,
                    "Particle target is not the rising item");
        }
    }

    @SuppressWarnings("unchecked")
    static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
