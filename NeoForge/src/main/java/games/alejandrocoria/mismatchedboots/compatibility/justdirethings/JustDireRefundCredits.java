package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

/** Whole purchased items, independent of installed capability and current ability availability. */
public record JustDireRefundCredits(List<String> names) {
    static final List<String> NAMES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    public static final Codec<JustDireRefundCredits> CODEC = Codec.STRING.listOf(0, 5)
                                                                         .comapFlatMap(JustDireRefundCredits::decode, JustDireRefundCredits::names);

    public JustDireRefundCredits {
        if (names.size() > NAMES.size() || names.stream().anyMatch(name -> !NAMES.contains(name))
                || names.stream().distinct().count() != names.size()) {
            throw new IllegalArgumentException("Unknown, duplicate or oversized JDT refund credit list");
        }
        names = NAMES.stream().filter(names::contains).toList();
    }

    private static DataResult<JustDireRefundCredits> decode(List<String> names) {
        try {
            return DataResult.success(new JustDireRefundCredits(names));
        } catch (IllegalArgumentException failure) {
            return DataResult.error(failure::getMessage);
        }
    }
}
