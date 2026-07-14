package dev.syntvalley.domain.building;

import dev.syntvalley.domain.resource.ResourceKey;
import java.util.Objects;

/**
 * One block of a template: where it sits relative to the origin, which resource it is made of, and which
 * build stage places it. Blocks are placed stage by stage, lowest stage first.
 */
public record TemplateBlock(BlockOffset offset, ResourceKey block, int stage) {
    public TemplateBlock {
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(block, "block");
        if (stage < 0) {
            throw new IllegalArgumentException("stage must not be negative");
        }
    }
}
