package ClientSideBlocks.view;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record BlockViewPosition(@NotNull UUID worldId, int x, int z) {
    public BlockViewPosition(@NotNull final Chunk chunk) {
        this(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
