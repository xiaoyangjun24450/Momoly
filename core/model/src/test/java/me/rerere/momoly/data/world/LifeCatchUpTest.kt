package me.rerere.momoly.data.world

import org.junit.Assert.*
import org.junit.Test

class LifeCatchUpTest {
    private val start = gameTime("2048-06-12T12:00:00")
    private val hour = 60 * GAME_MINUTE
    private val map = WorldMap(places = listOf(WorldPlace("home", "家")), userLocation = "home")
    private val initial = LifeInitial(initialTime = "2048-06-12T12:00:00", initialLocation = "home",
        background = "休息", history = "旧识")
    private val schedule = listOf(LifeSchedule("free", 0, 1440, "休息", "home"))

    private fun actor(nextContact: Long, relationship: LifeRelationship = LifeRelationship(lastSettledAt = start)) =
        LifeActor("a", "甲", initial, "home", "休息", schedule, 60, "平静", 40,
            nextContact = nextContact, relationship = relationship)

    private fun world(target: Long, a: LifeActor = actor(start + 30 * GAME_MINUTE)) =
        LifeWorld("w", map, start, target, actors = mapOf("a" to a))

    @Test fun advanceWithoutContactOpportunitiesOnlySettlesTime() {
        val plan = LifeCatchUp.plan(world(start + 6 * hour, actor(start + 48 * hour)))
        assertTrue(plan.slots.isEmpty())
        assertEquals(start + 6 * hour, plan.world.now)
        assertEquals(start + 6 * hour, plan.world.target)
    }

    @Test fun onePlanCoversTheWholeWindowAndSlotsAreDeterministic() {
        val w = world(start + 6 * hour)
        val plan = LifeCatchUp.plan(w)
        val slots = plan.slots.getValue("a")
        assertTrue("slots=$slots", slots.size >= 2)
        assertTrue(slots.all { it in (start + 30 * GAME_MINUTE)..(start + 6 * hour) })
        assertEquals(slots, LifeCatchUp.plan(w).slots.getValue("a"))
        assertEquals(start + 6 * hour, plan.world.now)
        assertTrue(plan.world.actors.getValue("a").nextContact > plan.world.target)
    }

    @Test fun planSettlesSilenceWithoutModelCalls() {
        val waiting = ReplyWait("s", 100, "在吗？", start, start + hour, 1)
        val relationship = LifeRelationship(profileSource = "card", waiting = waiting, lastSettledAt = start)
        val plan = LifeCatchUp.plan(world(start + 12 * hour, actor(start + 48 * hour, relationship)))
        val after = plan.world.actors.getValue("a")
        assertTrue(after.affinity < 60)
        assertTrue(after.relationship.anxiety > 0)
        assertEquals("irritated", after.relationship.stage)
        assertTrue(plan.slots.isEmpty())
    }

    @Test fun fencedJsonAndTrailingTextStillParse() {
        val fenced = "好的。\n```json\n{\"messages\":[\"在吗\"]}\n```\n以上。"
        assertEquals(listOf("在吗"), parseLifeTurnText(fenced)!!.messages)
        val catchUp = parseLifeTurnText("{\"catchUp\":[{\"slot\":1,\"text\":\"晚点聊\"}]}")
        assertEquals("晚点聊", catchUp!!.catchUp.single().text)
        assertNull(parseLifeTurnText("只有正文，没有 JSON"))
    }

    @Test fun catchUpMessagesHonoursSlotsFallsBackAndCaps() {
        val slots = listOf(start, start + hour, start + 2 * hour)
        val turn = LifeTurn(catchUp = listOf(
            LifeCatchUpMessage(2, "第三"),
            LifeCatchUpMessage(-1, "无序号"),
            LifeCatchUpMessage(0, "第一"),
        ))
        assertEquals(listOf(0L, 1L, 2L), catchUpMessages(turn, slots).map { slots.indexOf(it.first).toLong() })
        assertEquals(listOf("第一", "无序号", "第三"), catchUpMessages(turn, slots).map { it.second })

        val many = LifeTurn(messages = (1..8).map { "第${it}条" })
        assertEquals(6, catchUpMessages(many, slots).size)
    }

    @Test fun longOfflineSamplesOnlyAFewSlots() {
        val many = (1..60).map { start + it * hour }
        val sampled = LifeCatchUp.sampleSlots(many)
        assertEquals(6, sampled.size)
        assertEquals(many.first(), sampled.first())
        assertEquals(many.last(), sampled.last())
        assertEquals(many.take(4), LifeCatchUp.sampleSlots(many.take(4)))
    }

    @Test fun evidenceMatchingToleratesPunctuationAndWhitespace() {
        assertTrue(evidenceMatches("今晚七点观测台见", "好，今晚七点，观测台见。"))
        assertTrue(evidenceMatches("行 我带星图", "```行，我带星图。```"))
        assertFalse(evidenceMatches("明晚见", "今晚见"))
        assertFalse(evidenceMatches("", "今晚见"))
    }
}
