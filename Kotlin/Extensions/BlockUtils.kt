package Extensions

/**
 * Code example
 * This utility object provides extension functions for inline lambda editing of a Blocks
 * BlockData
 */

object BlockUtils {

    /**
     * Modifies the block's BlockData inline
     * @param action A lambda that receives the blocks current BlockData and allows direct modifications
     */
    inline fun <reified T : BlockData> Block.editBlockData(action: T.() -> Unit) {
        if (!editBlockDataSafe<T>(action))
            throw IllegalStateException("BlockData type mismatch: Expected ${T::class.java.simpleName} but found ${blockData::class.java.simpleName}")
    }

    /**
     * Safely modifies the block's BlockData inline
     * @param action A lambda that receives the blocks current BlockData and allows direct modifications.
     * @return true if the block data could be modified, false otherwise.
     */
    inline fun <reified T : BlockData> Block.editBlockDataSafe(action: T.() -> Unit): Boolean {
        val data = this.blockData
        if (data is T) {
            action(data)
            this.blockData = data
            return true
        }
        return false
    }

    // USAGE
    fun usage(block: Block) {
        block.editBlockData<Fire> {
            this.allowedFaces
        }
    }


}