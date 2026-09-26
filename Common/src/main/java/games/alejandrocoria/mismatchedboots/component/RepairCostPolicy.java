package games.alejandrocoria.mismatchedboots.component;

import java.util.Arrays;
import java.util.Optional;

public enum RepairCostPolicy {
    OWNER("owner"),
    AVERAGE("average"),
    MINIMUM("minimum"),
    MAXIMUM("maximum");

    private final String serializedName;

    RepairCostPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<RepairCostPolicy> fromSerializedName(String value) {
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst();
    }
}
