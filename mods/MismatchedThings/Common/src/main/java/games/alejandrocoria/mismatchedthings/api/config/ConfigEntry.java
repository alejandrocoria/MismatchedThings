package games.alejandrocoria.mismatchedthings.api.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.util.Objects;

/** A consumer-owned value that normalizes missing or invalid input to its default. */
public abstract class ConfigEntry<T> {
    private final String key;
    private final T defaultValue;
    private final String comment;
    private T value;

    protected ConfigEntry(String key, T defaultValue, String comment) {
        this.key = Objects.requireNonNull(key, "key");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.comment = comment;
        value = defaultValue;
    }

    public final boolean load(CommentedConfig config) {
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

    public final void save(CommentedConfig config) {
        config.set(key, serialize(value));
        if (comment != null) {
            config.setComment(key, comment);
        }
    }

    public final T get() {
        return value;
    }

    public final void reset() {
        value = defaultValue;
    }

    protected abstract T parse(Object rawValue);

    protected abstract Object serialize(T currentValue);
}
