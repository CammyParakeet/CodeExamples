package ClientSideBlocks.view;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;

@Getter
public abstract class AbstractBlockView implements BlockView {
    @Getter(AccessLevel.NONE)
    protected final Plugin plugin;

    protected final UUID id;
    protected final World world;
    protected final BlockPosition origin;
    protected final Vector3i dimensions;
    protected final BlockViewOptions options;
    protected final BlockDataRegistry blockDataRegistry;
    protected final BlockViewType type;
    protected final ViewBlockDigManager digManager;

    protected final Set<Audience> audiences = new HashSet<>();

    protected AbstractBlockView(
            @NotNull final Plugin plugin,
            @NotNull final UUID id,
            @NotNull final World world,
            @NotNull final BlockPosition origin,
            @NotNull final Vector3i dimensions,
            @NotNull final BlockViewOptions options,
            @NotNull final BlockDataRegistry blockDataRegistry,
            @NotNull final BlockViewType type
    ) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.origin = origin;
        this.dimensions = dimensions;
        this.options = options;
        this.blockDataRegistry = blockDataRegistry;
        this.type = type;
        this.digManager = new ViewBlockDigManager(plugin, this);
    }

    @Setter
    protected UUID ownerId;

    @Override
    public final @NotNull List<Player> getViewers() {
        final List<Player> players = new ArrayList<>();

        for (final Audience audience : this.audiences) {
            players.addAll(this.extractPlayersFromAudience(audience));
        }
        return players;
    }

    @Override
    public @NotNull Map<BlockPosition, ViewBlockData> getNearbyBlocks(
            final int xPosition,
            final int yPosition,
            final int zPosition,
            final int radius
    ) {
        final Map<BlockPosition, ViewBlockData> data = new HashMap<>();
        final double radiusSquared = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            final int worldX = xPosition + x;

            if (worldX < this.origin.blockX() || worldX >= this.origin.blockX() + this.dimensions.x) {
                continue;
            }
            for (int y = -radius; y <= radius; y++) {
                final int worldY = yPosition + y;

                if (worldY < this.origin.blockY() || worldY >= this.origin.blockY() + this.dimensions.y) {
                    continue;
                }
                for (int z = -radius; z <= radius; z++) {
                    final int worldZ = zPosition + z;

                    if (worldZ < this.origin.blockZ() || worldZ >= this.origin.blockZ() + this.dimensions.z) {
                        continue;
                    }
                    if (!this.isManagedBlock(worldX, worldY, worldZ)) {
                        continue;
                    }
                    final double deltaX = xPosition - worldX;
                    final double deltaY = yPosition - worldY;
                    final double deltaZ = zPosition - worldZ;
                    final double distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

                    if (distance > radiusSquared) {
                        continue;
                    }
                    data.put(
                            Position.block(worldX, worldY, worldZ),
                            this.getBlock(worldX, worldY, worldZ)
                    );
                }
            }
        }
        return data;
    }

    @Override
    @UnmodifiableView
    public final @NotNull Iterable<? extends Audience> audiences() {
        return Collections.unmodifiableSet(this.audiences);
    }

    /**
     * Adds an audience to view block updates within this region.
     * Internal use; managed by BlockViewManager.
     *
     * @param audience audience to add
     * @return true if added successfully; false otherwise
     */
    @Override
    @ApiStatus.Internal
    public final boolean addAudience(@NotNull final Audience audience, final boolean apply) {
        if (apply) {
            this.apply(audience);
        }
        return this.audiences.add(audience);
    }

    /**
     * Removes an audience from viewing block updates.
     * Internal use; managed by BlockViewManager.
     *
     * @param audience audience to remove
     * @param reset    whether to clear the packet blocks for the audience
     * @return true if removed successfully; false otherwise
     */
    @Override
    @ApiStatus.Internal
    public final boolean removeAudience(@NotNull final Audience audience, final boolean reset) {
        if (this.audiences.remove(audience)) {
            if (reset) {
                this.reset(audience);
            }
            return true;
        }
        return false;
    }

    protected final void checkBounds(final int x, final int y, final int z) {
        Preconditions.checkArgument(
                x >= this.origin.blockX() && x < this.origin.blockX() + this.dimensions.x,
                "X coordinate is out of bounds: " + x + ", " + y + ", " + z
        );
        Preconditions.checkArgument(
                y >= this.origin.blockY() && y < this.origin.blockY() + this.dimensions.y,
                "Y coordinate is out of bounds: " + x + ", " + y + ", " + z
        );
        Preconditions.checkArgument(
                z >= this.origin.blockZ() && z < this.origin.blockZ() + this.dimensions.z,
                "Z coordinate is out of bounds: " + x + ", " + y + ", " + z
        );
    }

    @Override
    public final boolean isInside(final int x, final int y, final int z) {
        try {
            this.checkBounds(x, y, z);

            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    protected final void sendPacket(@NotNull final Packet<?> packet) {
        final Collection<Player> viewers = this.getViewers();

        for (final Player viewer : viewers) {
            if (!viewer.getWorld().equals(this.world)) {
                continue;
            }
            PacketUtils.sendPacket(viewer, packet);
        }
    }

    protected final @NotNull Collection<@NotNull Player> extractPlayersFromAudience(@NotNull final Audience audience) {
        if (audience instanceof final Player player) {
            if (player.isOnline()) {
                return Collections.singleton(player);
            }
            return Collections.emptyList();
        }
        final Set<Player> players = new HashSet<>();

        audience.forEachAudience((audienceMember) -> {
            players.addAll(this.extractPlayersFromAudience(audienceMember));
        });

        return players;
    }
}
