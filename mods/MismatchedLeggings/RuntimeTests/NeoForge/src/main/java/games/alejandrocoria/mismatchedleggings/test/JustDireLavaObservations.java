package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Observation and one refused write, never an alternate source of repair authority. */
public final class JustDireLavaObservations {
    public static boolean capture;
    public static BlockPos refusedBlock;
    public static int refusedWrites;
    public static int sounds;
    private static final List<Emission> emissions = new ArrayList<>();

    private JustDireLavaObservations() {
    }

    public static void reset() {
        capture = true;
        refusedBlock = null;
        refusedWrites = 0;
        sounds = 0;
        emissions.clear();
    }

    public static void particle(ParticleOptions options, double x, double y, double z, int count,
                                double dx, double dy, double dz, double speed) {
        if (capture && options.getClass().getName().endsWith(".ItemFlowParticleData")) {
            require(count == 1 && dx == 0 && dy == 0 && dz == 0 && speed == 0, "Wrong native emission spread/count");
            emissions.add(new Emission(options, x, y, z));
        }
    }

    public static void validate(BlockPos source) throws ReflectiveOperationException {
        validate(source, false);
    }

    public static void validate(BlockPos source, boolean nativeOriginal) throws ReflectiveOperationException {
        require(!emissions.isEmpty() && emissions.size() % 5 == 0, "Missing native particle density");
        int active = 0;
        for (var emission : emissions) {
            // JDT's helper also calls its emitter after clearing progress/anchor. Do not require
            // that unrelated native idle bug in mixed items, or tolerate it in their strict gate.
            if (nativeOriginal && emission.y == .95 && emission.x >= 0 && emission.x < 1 && emission.z >= 0 && emission.z < 1) {
                continue;
            }
            active++;
            require(emission.x >= source.getX() && emission.x < source.getX() + 1
                            && emission.z >= source.getZ() && emission.z < source.getZ() + 1 && emission.y == source.getY() + .95,
                    "Particle origin escaped actual source " + source + ": " + emission);
            var options = emission.options;
            require(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).getNamespace().equals("justdirethings"), "Non-native particle type");
            var item = (ItemStack) options.getClass().getMethod("getItemStack").invoke(options);
            require(BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().equals("gooblock_tier2")
                            && (int) options.getClass().getMethod("getTicksPerBlock").invoke(options) == 3,
                    "Native visual item/speed changed");
            require((double) options.getClass().getMethod("getTargetX").invoke(options) == source.getX() + .5
                            && (double) options.getClass().getMethod("getTargetZ").invoke(options) == source.getZ() + .5
                            && (double) options.getClass().getMethod("getTargetY").invoke(options) > source.getY() + .75,
                    "Native particles did not target the rising item");
        }
        require(active > 0, "No particles from the actual ritual source");
    }

    public static int particleCount() {
        return emissions.size();
    }

    private record Emission(ParticleOptions options, double x, double y, double z) {
    }
}
