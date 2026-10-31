package games.alejandrocoria.mismatchedthings.api.config;

import com.electronwill.nightconfig.core.CommentedConfig;

/** Consumer-owned config schema/state; load returns whether normalized values need saving. */
public interface DynamicConfigPart {
    boolean load(CommentedConfig config);

    void save(CommentedConfig config);

    void reset();
}
