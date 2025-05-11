package ClientSideBlocks.data;

import org.jetbrains.annotations.NotNull;

public final class VanillaViewBlockData implements ViewBlockData {
    public static final ViewBlockData AIR = new VanillaViewBlockData(Material.AIR.createBlockData());

    private final BlockData blockData;

    public VanillaViewBlockData(@NotNull final BlockData blockData) {
        this.blockData = blockData;
    }

    @Override
    public @NotNull String serialize() {
        return this.blockData.getAsString();
    }

    @Override
    public @NotNull Material getType() {
        return this.blockData.getMaterial();
    }

    @Override
    public float getHardness() {
        return this.blockData.getMaterial().getHardness();
    }

    @Override
    public boolean canHarvestWithItem(@NotNull final ItemStack itemStack) {
        return this.blockData.isPreferredTool(itemStack);
    }

    @Override
    public @NotNull BlockData toBlockData() {
        return this.blockData;
    }
}
