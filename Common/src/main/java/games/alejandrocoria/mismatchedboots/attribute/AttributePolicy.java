package games.alejandrocoria.mismatchedboots.attribute;

import java.util.Arrays;
import java.util.Optional;

public enum AttributePolicy {
    AVERAGE("average"),
    COMBINE("combine"),
    MINIMUM("minimum"),
    MAXIMUM("maximum"),
    SEPARATE("separate"),
    REJECT("reject");

    private final String serializedName;

    AttributePolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<AttributePolicy> fromSerializedName(String value) {
        return Arrays.stream(values())
                     .filter(policy -> policy.serializedName.equals(value))
                     .findFirst();
    }
}
