package dev.syntvalley.domain.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RotationTest {
    @Test
    void noneIsIdentity() {
        BlockOffset offset = new BlockOffset(2, 1, 0);
        assertEquals(offset, Rotation.NONE.apply(offset));
    }

    @Test
    void clockwise90MatchesMinecraftConvention() {
        assertEquals(new BlockOffset(0, 5, 2), Rotation.CLOCKWISE_90.apply(new BlockOffset(2, 5, 0)));
    }

    @Test
    void clockwise180NegatesHorizontalKeepsHeight() {
        assertEquals(new BlockOffset(-2, 4, -1), Rotation.CLOCKWISE_180.apply(new BlockOffset(2, 4, 1)));
    }

    @Test
    void fourQuarterTurnsReturnToStart() {
        BlockOffset offset = new BlockOffset(2, 3, 1);
        BlockOffset turned = Rotation.CLOCKWISE_90.apply(
                Rotation.CLOCKWISE_90.apply(Rotation.CLOCKWISE_90.apply(Rotation.CLOCKWISE_90.apply(offset))));
        assertEquals(offset, turned);
    }
}
