package games.alejandrocoria.mismatchedboots.component;

import java.util.Arrays;
import java.util.Optional;

public enum ComponentPolicy {
    OWNER("owner"),
    OMIT("omit"),
    SAME("same"),
    SHARE("share"),
    AVERAGE("average"),
    MINIMUM("minimum"),
    MAXIMUM("maximum"),
    INTERSECTION("intersection");

    private final String serializedName;

    ComponentPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<ComponentPolicy> fromSerializedName(String value) {
        return Arrays.stream(values())
                .filter(policy -> policy.serializedName.equals(value))
                .findFirst();
    }

    public boolean isNumeric() {
        return this == AVERAGE || this == MINIMUM || this == MAXIMUM;
    }
}
