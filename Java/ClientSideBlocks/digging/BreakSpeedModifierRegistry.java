package ClientSideBlocks.digging;

import ClientSideBlocks.data.ViewBlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BreakSpeedModifierRegistry {
    void register(@NotNull ToolBreakSpeedModifier modifier);
    float applyModifiers(
            @NotNull ViewBlockData blockData,
            @NotNull ItemStack tool,
            @Nullable Player player,
            float baseSpeed
    );
}
