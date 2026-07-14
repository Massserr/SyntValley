package dev.syntvalley.application.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.building.BuildingTemplate;
import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.resource.ResourceKey;
import org.junit.jupiter.api.Test;

class BuildingCatalogTest {
    private static final ResourceKey PLANKS = new ResourceKey("minecraft:oak_planks");
    private static final ResourceKey FENCE = new ResourceKey("minecraft:oak_fence");

    @Test
    void smallStorehouseHasExpectedShapeAndBill() {
        BuildingTemplate template = BuildingCatalog.smallStorehouse();

        assertEquals(BuildingCatalog.SMALL_STOREHOUSE, template.id());
        assertEquals(1, template.version());
        assertEquals(26, template.blockCount());
        assertEquals(3, template.stageCount());
        assertEquals(9, template.blocksForStage(0).size());
        assertEquals(8, template.blocksForStage(1).size());
        assertEquals(9, template.blocksForStage(2).size());
        assertEquals(18, template.bill().get(PLANKS).intValue());
        assertEquals(8, template.bill().get(FENCE).intValue());
    }

    @Test
    void lookupByIdReturnsTemplateOrEmpty() {
        assertTrue(BuildingCatalog.get(BuildingCatalog.SMALL_STOREHOUSE).isPresent());
        assertTrue(BuildingCatalog.get(new BuildingTemplateId("syntvalley:unknown")).isEmpty());
    }
}
