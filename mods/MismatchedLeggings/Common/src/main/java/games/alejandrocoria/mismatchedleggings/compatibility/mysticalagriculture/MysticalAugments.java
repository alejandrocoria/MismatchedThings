package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit native families: effective abilities and occupied physical slots are different views. */
public final class MysticalAugments {
    public enum Route {ARMOR, PLAYER}

    public enum Family {
        ABSORPTION(Route.ARMOR), FIRE_RESISTANCE(Route.ARMOR), HEALTH_BOOST(Route.PLAYER),
        LUCK(Route.ARMOR), MINING_FATIGUE_RESISTANCE(Route.ARMOR),
        POISON_RESISTANCE(Route.ARMOR), SLOWNESS_RESISTANCE(Route.ARMOR), SPEED(Route.PLAYER),
        STEP_ASSIST(Route.PLAYER), WITHER_RESISTANCE(Route.ARMOR);

        public final Route route;

        Family(Route route) {
            this.route = route;
        }

        public String nativeClass() {
            return "com.blakebr0.mysticalagriculture.augment." + switch (this) {
                case ABSORPTION -> "AbsorptionAugment";
                case FIRE_RESISTANCE -> "FireResistanceAugment";
                case HEALTH_BOOST -> "HealthBoostAugment";
                case LUCK -> "LuckAugment";
                case MINING_FATIGUE_RESISTANCE -> "MiningFatigueResistanceAugment";
                case POISON_RESISTANCE -> "PoisonResistanceAugment";
                case SLOWNESS_RESISTANCE -> "SlownessResistanceAugment";
                case SPEED -> "SpeedAugment";
                case STEP_ASSIST -> "StepAssistAugment";
                case WITHER_RESISTANCE -> "WitherResistanceAugment";
            };
        }
    }

    public record Spec(Identifier id, Family family, int strength, int requiredTier) {
    }

    public static final Set<String> LEGGINGS = Set.of("inferium_leggings", "prudentium_leggings", "tertium_leggings",
                                                   "imperium_leggings", "supremium_leggings", "awakened_supremium_leggings");
    public static final Map<Identifier, Spec> CATALOG = catalog();

    private MysticalAugments() {
    }

    private static Map<Identifier, Spec> catalog() {
        var result = new LinkedHashMap<Identifier, Spec>();
        for (Family family : Family.values()) {
            int count = switch (family) {
                case ABSORPTION, HEALTH_BOOST -> 5;
                case SPEED, LUCK -> 3;
                default -> 1;
            };
            for (int rank = 1; rank <= count; rank++) {
                String suffix = count == 1 ? "" : "_" + List.of("i", "ii", "iii", "iv", "v").get(rank - 1);
                Identifier id = Identifier.fromNamespaceAndPath("mysticalagriculture",
                                                                family.name().toLowerCase(java.util.Locale.ROOT) + suffix);
                result.put(id, new Spec(id, family, rank, switch (family) {
                    case ABSORPTION, HEALTH_BOOST -> rank;
                    case LUCK -> rank * 2 - 1;
                    case SPEED -> rank + 1;
                    case FIRE_RESISTANCE, SLOWNESS_RESISTANCE, STEP_ASSIST -> 3;
                    case MINING_FATIGUE_RESISTANCE, POISON_RESISTANCE -> 4;
                    case WITHER_RESISTANCE -> 5;
                }));
            }
        }
        return Map.copyOf(result);
    }

    public static List<Spec> select(List<Spec> first, List<Spec> second) {
        var best = new EnumMap<Family, Spec>(Family.class);
        for (List<Spec> source : List.of(first, second)) {
            for (Spec spec : source) {
                best.merge(spec.family(), spec, (a, b) -> a.strength() >= b.strength() ? a : b);
            }
        }
        return List.copyOf(new ArrayList<>(best.values()));
    }
}
