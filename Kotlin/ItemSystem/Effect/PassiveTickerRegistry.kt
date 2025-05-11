object PassiveTickerRegistry : SimpleRegistry<TickingPassiveEffect>(), Tickable {

    override fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            for (effect in getAll()) {
                if (!effect.shouldTick()) continue
                val context = effect.getContext(player)
                val shouldBeActive = effect.shouldRemainActive(context)

                if (shouldBeActive) {
                    effect.startTicking(player)
                } else {
                    effect.stopTicking(player)
                }

                effect.tick(context)
            }
        }
    }

}