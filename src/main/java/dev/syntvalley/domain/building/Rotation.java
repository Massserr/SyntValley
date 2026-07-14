package dev.syntvalley.domain.building;

import java.util.Objects;

/**
 * Rotation about the vertical axis. The offset math matches Minecraft's {@code BlockPos} rotation so the
 * placement adapter is a 1:1 mapping and templates look identical whichever way a site faces.
 */
public enum Rotation {
    NONE,
    CLOCKWISE_90,
    CLOCKWISE_180,
    CLOCKWISE_270;

    public BlockOffset apply(BlockOffset offset) {
        Objects.requireNonNull(offset, "offset");
        return switch (this) {
            case NONE -> offset;
            case CLOCKWISE_90 -> new BlockOffset(-offset.z(), offset.y(), offset.x());
            case CLOCKWISE_180 -> new BlockOffset(-offset.x(), offset.y(), -offset.z());
            case CLOCKWISE_270 -> new BlockOffset(offset.z(), offset.y(), -offset.x());
        };
    }
}
