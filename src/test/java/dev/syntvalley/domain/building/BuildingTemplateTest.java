package dev.syntvalley.domain.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.syntvalley.domain.resource.ResourceKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildingTemplateTest {
    private static final BuildingTemplateId ID = new BuildingTemplateId("syntvalley:test_hut");
    private static final ResourceKey PLANKS = new ResourceKey("minecraft:oak_planks");
    private static final ResourceKey FENCE = new ResourceKey("minecraft:oak_fence");

    @Test
    void stagesBillAndCountsDerive() {
        BuildingTemplate template = new BuildingTemplate(ID, 1, List.of(
                new TemplateBlock(new BlockOffset(0, 0, 0), PLANKS, 0),
                new TemplateBlock(new BlockOffset(1, 0, 0), PLANKS, 0),
                new TemplateBlock(new BlockOffset(0, 1, 0), FENCE, 1)));

        assertEquals(3, template.blockCount());
        assertEquals(2, template.stageCount());
        assertEquals(2, template.blocksForStage(0).size());
        assertEquals(1, template.blocksForStage(1).size());
        assertEquals(Map.of(PLANKS, 2, FENCE, 1), template.bill());
    }

    @Test
    void rejectsDuplicateOffsets() {
        assertThrows(IllegalArgumentException.class, () -> new BuildingTemplate(ID, 1, List.of(
                new TemplateBlock(new BlockOffset(0, 0, 0), PLANKS, 0),
                new TemplateBlock(new BlockOffset(0, 0, 0), FENCE, 1))));
    }

    @Test
    void rejectsEmptyTemplateAndBadVersion() {
        assertThrows(IllegalArgumentException.class, () -> new BuildingTemplate(ID, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BuildingTemplate(ID, 0, List.of(
                new TemplateBlock(new BlockOffset(0, 0, 0), PLANKS, 0))));
    }

    @Test
    void billIsImmutable() {
        BuildingTemplate template = new BuildingTemplate(ID, 1, List.of(
                new TemplateBlock(new BlockOffset(0, 0, 0), PLANKS, 0)));
        assertThrows(UnsupportedOperationException.class, () -> template.bill().put(FENCE, 1));
    }
}
