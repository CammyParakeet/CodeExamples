package ClientSideBlocks.view;

@Getter
@Builder
public final class BlockViewOptions {
    public static BlockViewOptions defaults() {
        return BlockViewOptions.builder().build();
    }

    @Builder.Default
    private BlockBreakMode blockBreakMode = BlockBreakMode.ENABLED;

    @Builder.Default
    private BlockPlacementMode blockPlacementMode = BlockPlacementMode.ENABLED;

    @Builder.Default
    private UnmanagedBlockBehavior unmanagedBlockBehavior = UnmanagedBlockBehavior.CANCEL;

    public enum BlockPlacementMode {
        ENABLED,
        DISABLED;
    }

    public enum BlockBreakMode {
        ENABLED,
        DISABLED,
        NEW_ONLY;
    }

    public enum UnmanagedBlockBehavior {
        ALLOW,
        CANCEL;
    }
}
