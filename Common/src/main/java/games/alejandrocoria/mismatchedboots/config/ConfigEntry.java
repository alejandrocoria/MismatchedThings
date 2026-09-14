package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.util.Objects;

abstract class ConfigEntry<T> {
    private final String key;
    private final T defaultValue;
    private final String comment;
    private T value;

    ConfigEntry(String key, T defaultValue, String comment) {
        this.key = Objects.requireNonNull(key, "key");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.comment = comment;
        value = defaultValue;
    }

    final boolean load(CommentedConfig config) {
        Object rawValue = config.valueMap().get(key);
        if (rawValue == null) {
            reset();
            return true;
        }

        T parsedValue = parse(rawValue);
        if (parsedValue == null) {
            reset();
            return true;
        }

        value = parsedValue;
        return false;
    }

    final void save(CommentedConfig config) {
        config.set(key, serialize(value));
        if (comment != null) {
            config.setComment(key, comment);
        }
    }

    final T get() {
        return value;
    }

    final void reset() {
        value = defaultValue;
    }

    protected abstract T parse(Object rawValue);

    protected abstract Object serialize(T currentValue);
}
