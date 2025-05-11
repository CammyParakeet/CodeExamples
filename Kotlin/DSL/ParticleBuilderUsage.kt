package DSL

import java.util.Random
import java.util.concurrent.ThreadLocalRandom

object ParticleBuilderUsage {

    val random: Random = ThreadLocalRandom.current();

    fun spawnHoldingEffects(loc: Location) {
        loc.particles {
            if (random.nextBoolean()) effect(Particle.SMOKE_NORMAL) {
                count(5); offset(0.2, 0.3, 0.2); extra(0.02)
            }
            if (random.nextDouble() < 0.4) effect(Particle.WHITE_SMOKE) {
                count(1); offset(0.3, 0.5, 0.3); extra(0.03)
            }
            effect(Particle.ASH) {
                count(4); offset(0.3, 0.5, 0.3); extra(0.02)
            }
            if (random.nextBoolean()) effect(Particle.WHITE_ASH) {
                count(1); offset(0.3, 0.5, 0.3); extra(0.03)
            }
            if (random.nextDouble() < 0.2) effect(Particle.LAVA) {
                count(1); offset(0.2, 0.2, 0.2); extra(0.0)
            }
        }
    }

}