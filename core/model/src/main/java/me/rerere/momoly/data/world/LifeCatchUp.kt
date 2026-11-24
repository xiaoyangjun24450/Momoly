package me.rerere.momoly.data.world

/** 一次合并补齐的确定性计划：推进后的世界与各角色在离开期间的联系时机。 */
data class LifeCatchUpPlan(val world: LifeWorld, val slots: Map<String, List<Long>>)

/**
 * 离线补齐先在内存里推进整段时间：只结算程序能确定的变化并收集联系时机，
 * 不写盘、不请求模型。调用方再按角色各发一次合并请求。
 */
object LifeCatchUp {
    /** 超长离线只取均匀分布的几个时机送模型，避免提示词列出几十条。 */
    fun sampleSlots(slots: List<Long>, max: Int = 6): List<Long> {
        if (slots.size <= max) return slots
        return (0 until max).map { slots[it * (slots.size - 1) / (max - 1)] }.distinct()
    }

    fun plan(world: LifeWorld, maxSteps: Int = 20_000): LifeCatchUpPlan {
        var sim = world
        val slots = mutableMapOf<String, MutableList<Long>>()
        var guard = 0
        while (sim.now < sim.target && guard++ < maxSteps) {
            val next = LifeRules.nextBoundary(sim).coerceAtMost(sim.target)
            if (next <= sim.now) break
            sim = LifeRules.advance(sim, next)
            if (sim.now >= sim.target) break
            for (actor in sim.actors.values.toList()) {
                if (actor.nextContact <= sim.now) {
                    if (RelationshipRules.allowsContact(actor, sim.now)) {
                        slots.getOrPut(actor.file) { mutableListOf() }.add(sim.now)
                    }
                    sim = sim.copy(actors = sim.actors + (actor.file to actor.copy(
                        nextContact = LifeRules.nextContact(actor, sim.now))))
                }
            }
        }
        if (sim.now < world.target) sim = LifeRules.advance(sim, world.target)
        return LifeCatchUpPlan(sim, slots)
    }
}
