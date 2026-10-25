package games.alejandrocoria.mismatchedboots.component;

import java.util.Arrays;
import java.util.Optional;

public enum DurabilityPolicy {
    AVERAGE("average"),
    SEPARATE("separate");

    private final String serializedName;

    DurabilityPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<DurabilityPolicy> fromSerializedName(String value) {
        return Arrays.stream(values())
                     .filter(policy -> policy.serializedName.equals(value))
                     .findFirst();
    }
}
