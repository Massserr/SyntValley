package dev.syntvalley.domain.building;

/**
 * A Minecraft-free block offset relative to a template origin. The placement adapter maps it to a real
 * {@code BlockPos} once an origin and rotation are chosen.
 */
public record BlockOffset(int x, int y, int z) {
    public BlockOffset plus(BlockOffset other) {
        return new BlockOffset(x + other.x, y + other.y, z + other.z);
    }
}
