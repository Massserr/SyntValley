package dev.syntvalley.observability;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Common logger entry point. It contains no world or gameplay state.
 */
public final class SyntValleyLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SyntValleyLog() {
    }

    public static Logger logger() {
        return LOGGER;
    }
}
