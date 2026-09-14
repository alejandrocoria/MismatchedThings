package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TomlConfigIO {
    private TomlConfigIO() {
    }

    static CommentedFileConfig open(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build();
    }
}
