package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.CommentedConfig;

interface DynamicConfigPart {
    boolean load(CommentedConfig config);

    void save(CommentedConfig config);

    void reset();
}
