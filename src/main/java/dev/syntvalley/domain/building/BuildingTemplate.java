package dev.syntvalley.domain.building;

import dev.syntvalley.domain.resource.ResourceKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A versioned, immutable building template: its geometry, the staged order in which blocks go up, and the
 * bill of materials derived from that geometry. The version guards persisted projects against a template
 * changing shape underneath them.
 */
public record BuildingTemplate(BuildingTemplateId id, int version, List<TemplateBlock> blocks) {
    public BuildingTemplate {
        Objects.requireNonNull(id, "id");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("template must have at least one block");
        }
        Set<BlockOffset> seen = new HashSet<>();
        for (TemplateBlock block : blocks) {
            if (!seen.add(block.offset())) {
                throw new IllegalArgumentException("duplicate offset in template: " + block.offset());
            }
        }
    }

    /** Number of stages (max stage index + 1); blocks are placed from stage 0 upward. */
    public int stageCount() {
        int max = 0;
        for (TemplateBlock block : blocks) {
            max = Math.max(max, block.stage());
        }
        return max + 1;
    }

    /** The blocks placed during one stage, in template order. */
    public List<TemplateBlock> blocksForStage(int stage) {
        List<TemplateBlock> result = new ArrayList<>();
        for (TemplateBlock block : blocks) {
            if (block.stage() == stage) {
                result.add(block);
            }
        }
        return List.copyOf(result);
    }

    /** Total materials the project needs, aggregated by resource — one unit per template block. */
    public Map<ResourceKey, Integer> bill() {
        Map<ResourceKey, Integer> bill = new LinkedHashMap<>();
        for (TemplateBlock block : blocks) {
            bill.merge(block.block(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(bill);
    }

    public int blockCount() {
        return blocks.size();
    }
}
