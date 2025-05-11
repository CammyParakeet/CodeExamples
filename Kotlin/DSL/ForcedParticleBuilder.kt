package DSL

/**
 * A DSL utility for constructing and spawning forced particle effects with specified properties.
 * This builder allows for the creation of complex particle effects through a fluent interface,
 * supporting customization of particle types, offsets, receiver range, and additional configuration
 * via a builder action.
 *
 * @param location The initial location for the particle effect.
 * @author Cammy
 * @since 1.0
 */
class ForcedParticleBuilder(private val location: Location) {

    private val effects = mutableListOf<ParticleBuilder.() -> Unit>()

    /**
     * Adds a new particle effect configuration to this builder.
     *
     * Allows for detailed customization of particle effects, including type, offset, receiver range,
     * and additional properties defined within a [ParticleBuilder]. Each call adds a new effect
     * configuration, which can be independently customized.
     *
     * @param particle The type of particle for the effect, defaulting to [Particle.ASH].
     * @param offset A [Vector] representing the offset from the initial location for the particle effect.
     * @param receiverRange The maximum range at which players can receive and see the particle effect.
     * @param builderAction A lambda with receiver on [ParticleBuilder] for additional effect customization.
     * @return Returns this [ForcedParticleBuilder] instance for fluent chaining.
     */
    fun effect(
        particle: Particle = Particle.ASH,
        offset: Vector = Vector(),
        receiverRange: Int = 60,
        builderAction: ParticleBuilder.() -> Unit
    ): ForcedParticleBuilder {
        val extendedAction: ParticleBuilder.() -> Unit = {
            particle(particle)
            location(location.clone().add(offset))
            force(true)
            //receivers(receiverRange)
            builderAction()
        }
        effects.add(
            extendedAction
        )
        return this
    }

    /**
     * Spawns all configured particle effects at their respective locations.
     *
     * Iterates through all added particle effect configurations, constructing and spawning each
     * according to its defined properties.
     */
    fun spawnAll() {
        effects.forEach { action ->
            val builder = ParticleBuilder(Particle.ASH)
            builder.action()
            builder.spawn()
        }
    }

}

/**
 * A DSL function for easily creating and spawning particle effects at a specific [Location].
 *
 * This function provides a concise way to use [ForcedParticleBuilder] directly on a [Location],
 * allowing for in-place definition and spawning of particle effects.
 *
 * @param setup A lambda with receiver on [ForcedParticleBuilder] for defining particle effects.
 */
fun Location.particles(setup: ForcedParticleBuilder.() -> Unit) {
    runAsync {
        val builder = ForcedParticleBuilder(this@particles)
        builder.setup()
        builder.spawnAll()
    }
}