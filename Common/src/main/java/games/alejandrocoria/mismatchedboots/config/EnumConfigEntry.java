package games.alejandrocoria.mismatchedboots.config;

import java.util.Optional;
import java.util.function.Function;

final class EnumConfigEntry<T extends Enum<T>> extends ConfigEntry<T> {
    private final Function<String, Optional<T>> parser;
    private final Function<T, String> serializer;

    EnumConfigEntry(String key, T defaultValue, String comment,
                    Function<String, Optional<T>> parser, Function<T, String> serializer) {
        super(key, defaultValue, comment);
        this.parser = parser;
        this.serializer = serializer;
    }

    @Override
    protected T parse(Object rawValue) {
        if (!(rawValue instanceof String value)) {
            return null;
        }
        return parser.apply(value).orElse(null);
    }

    @Override
    protected Object serialize(T currentValue) {
        return serializer.apply(currentValue);
    }
}
