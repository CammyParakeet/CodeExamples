package ClientSideBlocks.utils;

public enum TriggerSource {
    PLAYER,
    EFFECT,
    COMMAND,
    SCRIPT;

    public boolean isPlayer() {
        return this == TriggerSource.PLAYER;
    }

    public boolean isEffect() {
        return this == TriggerSource.EFFECT;
    }

}
