package games.alejandrocoria.mismatchedboots.component;

import java.util.Arrays;
import java.util.Optional;

public enum ComponentNumericKind {
    INT("int"),
    LONG("long");

    private final String serializedName;

    ComponentNumericKind(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<ComponentNumericKind> fromSerializedName(String value) {
        return Arrays.stream(values())
                     .filter(kind -> kind.serializedName.equals(value))
                     .findFirst();
    }
}
