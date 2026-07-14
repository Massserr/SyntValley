package dev.syntvalley.application.building;

import dev.syntvalley.domain.building.BlockOffset;
import dev.syntvalley.domain.building.BuildingTemplate;
import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.TemplateBlock;
import dev.syntvalley.domain.resource.ResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Java-registered building templates. Slice 8 ships one production-quality template — a small open
 * shelter — built in three stages: floor, corner posts, roof. A datapack-driven catalog can replace this
 * later without changing callers; upgraded/larger buildings are added here as new versioned templates.
 */
public final class BuildingCatalog {
    public static final BuildingTemplateId SMALL_STOREHOUSE = new BuildingTemplateId("syntvalley:small_storehouse");

    private static final ResourceKey PLANKS = new ResourceKey("minecraft:oak_planks");
    private static final ResourceKey POST = new ResourceKey("minecraft:oak_fence");

    private BuildingCatalog() {
    }

    /** Looks up a built-in template by id. */
    public static Optional<BuildingTemplate> get(BuildingTemplateId id) {
        return SMALL_STOREHOUSE.equals(id) ? Optional.of(smallStorehouse()) : Optional.empty();
    }

    /**
     * A 3x3 open shelter: a plank floor (stage 0), four fence-post corners two blocks tall (stage 1),
     * and a plank roof (stage 2). 26 blocks total; bill: 18 oak planks and 8 oak fences.
     */
    public static BuildingTemplate smallStorehouse() {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                blocks.add(new TemplateBlock(new BlockOffset(x, 0, z), PLANKS, 0));
            }
        }
        int[][] corners = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};
        for (int[] corner : corners) {
            for (int y = 1; y <= 2; y++) {
                blocks.add(new TemplateBlock(new BlockOffset(corner[0], y, corner[1]), POST, 1));
            }
        }
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                blocks.add(new TemplateBlock(new BlockOffset(x, 3, z), PLANKS, 2));
            }
        }
        return new BuildingTemplate(SMALL_STOREHOUSE, 1, blocks);
    }
}
