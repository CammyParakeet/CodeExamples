package ClientSideBlocks.digging;

@RequiredArgsConstructor
public final class ViewBlockSessionSyncTask implements Runnable {
    private final ViewBlockDigManager digManager;

    @Override
    public void run() {
        this.digManager.getBlockRecords().forEach(ViewBlockDigManager.BlockRecord::sync);
    }
}
