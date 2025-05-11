package ClientSideBlocks.digging;

import ClientSideBlocks.data.ViewBlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ToolBreakSpeedModifier {
    float modifySpeed(
            @NotNull ViewBlockData blockData,
            @NotNull ItemStack tool,
            @Nullable Player player,
            float baseToolSpeed
    );
}
