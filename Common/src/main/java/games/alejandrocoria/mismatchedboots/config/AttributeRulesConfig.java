package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;

import java.nio.file.Path;
import java.util.Objects;

public final class AttributeRulesConfig {
    public static final String FILE_NAME = Constants.MOD_ID + "-attributes.toml";
    private final Path path;
    private final TomlConfigIO files;
    private final AttributeRulesConfigPart attributes = new AttributeRulesConfigPart();

    public AttributeRulesConfig(Path path) {
        this(path, new TomlConfigIO());
    }

    AttributeRulesConfig(Path path, TomlConfigIO files) {
        this.path = Objects.requireNonNull(path, "path");
        this.files = Objects.requireNonNull(files, "files");
    }

    public AttributeRuleSet load() {
        files.load(path, attributes);
        return attributes.resolved();
    }

    public Path path() {
        return path;
    }

}
