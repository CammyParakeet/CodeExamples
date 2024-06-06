package Extensions

object DisplayUtils {

    /**
     * Modifies the display entity's transformation properties through a lambda expression.
     * This method provides a flexible way to adjust the display's transformation.
     *
     * Regular usage is far more tedious
     *
     * @param action A lambda with receiver on [Transformation] to customize the display entity's transformation.
     */
    fun Display.editTransform(action: Transformation.() -> Unit) {
        val trans = transformation
        trans.action()
        transformation = trans
    }

    // USAGE
    fun usage(display: Display) {
        display.editTransform {
            translation.set(1.0, 1.0, 1.0)
            scale.set(3.2, 5.0, 2.0)
        }
    }

}