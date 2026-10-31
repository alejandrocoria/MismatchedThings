package games.alejandrocoria.mismatchedleggings.config;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedthings.api.config.TomlConfigIO;

import java.nio.file.Path;
import java.util.Objects;

public final class AttributeRulesConfig {
    public static final String FILE_NAME = LeggingsContent.MOD_ID + "-attributes.toml";
    private final Path path;
    private final TomlConfigIO files;
    private final AttributeRulesConfigPart attributes = new AttributeRulesConfigPart();

    public AttributeRulesConfig(Path path) {
        this(path, new TomlConfigIO(LeggingsContent.LOG));
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
