package me.rerere.momoly.data.world

import kotlinx.serialization.encodeToString
import me.rerere.momoly.data.chat.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class LifeRelationshipTest {
    private val start = gameTime("2048-06-12T12:00:00")
    private val hour = 60 * GAME_MINUTE
    private val initial = LifeInitial(initialTime="2048-06-12T12:00:00", initialLocation="home", background="休息", history="旧识")
    private val actor = LifeActor("a", "甲", initial, "home", "休息", emptyList(), 80, "平静", 40,
        nextContact=start, sessionId="session", relationship=LifeRelationship(lastSettledAt=start))
    private fun world(a: LifeActor = actor) = LifeWorld("world", WorldMap(places=listOf(WorldPlace("home","家")),userLocation="home"),
        start, start, actors=mapOf("a" to a))
    private fun delivery(ts: Long = 100, text: String = "今晚聊吗？", evidence: String = "今晚聊吗？") =
        LifeDelivery("session", ChatMessage("assistant",text,ts=ts), start, evidence, startsContact=true, proactive=true)
    private fun waiting(a: LifeActor = actor) = RelationshipRules.delivered(world(a), delivery(), start)
    private fun LifeWorld.a() = actors.getValue("a")

    @Test fun onlyDeliveredEvidenceStartsWaitingAndFollowupsKeepOriginalDeadline() {
        assertNull(RelationshipRules.delivered(world(),delivery(evidence="虚构"),start).a().relationship.waiting)
        assertNull(RelationshipRules.delivered(world(),delivery(evidence=""),start).a().relationship.waiting)
        val first = waiting()
        val second = RelationshipRules.delivered(first,delivery(101),start+hour)
        assertEquals(start,second.a().relationship.waiting!!.deliveredAt)
        assertEquals(start+6*hour,second.a().relationship.waiting!!.expectedReplyAt)
        assertEquals(2,second.a().relationship.waiting!!.unansweredContacts)
        assertTrue(second.a().relationship.contactFactor < first.a().relationship.contactFactor)
    }

    @Test fun historicalMessagesStartAtActualDeliveryRatherThanStoryTime() {
        val actual = start+72*hour
        val delivered = RelationshipRules.delivered(world(),delivery(),actual)
        val catchup = RelationshipRules.advance(delivered,actual)
        assertEquals(80,catchup.a().affinity)
        assertEquals(actual,catchup.a().relationship.waiting!!.deliveredAt)
        assertEquals(0.0,catchup.a().relationship.anxiety,0.0)
    }

    @Test fun silenceAloneChangesStateAndReplayingTimeCannotChargeAgain() {
        val w = RelationshipRules.advance(waiting(),start+30*hour)
        assertEquals(76,w.a().affinity)
        assertEquals("irritated",w.a().relationship.stage)
        assertEquals("失落且烦躁",w.a().mood)
        assertTrue(w.a().relationship.anxiety > 0)
        assertTrue(w.a().relationship.contactFactor < .5)
        assertEquals(w,RelationshipRules.advance(w,start+30*hour))
        assertEquals(w,RelationshipRules.advance(w,start+29*hour))
    }

    @Test fun segmentedCatchupAndSerializedRestartMatchOneAdvance() {
        val once=RelationshipRules.advance(waiting(),start+72*hour)
        var chunked=waiting()
        for(i in 1..144) {
            chunked=RelationshipRules.advance(chunked,start+i*30*GAME_MINUTE)
            chunked=lifeJson.decodeFromString(lifeJson.encodeToString(chunked))
        }
        assertEquals(once.a().affinity,chunked.a().affinity)
        assertEquals(once.a().relationship.copy(latestChange=""),chunked.a().relationship.copy(latestChange=""))
        assertEquals(once.a().mood,chunked.a().mood)
    }

    @Test fun personalityChangesToleranceAndReactionAndLongAbsenceHasBoundedLoss() {
        val sensitive=actor.copy(relationship=actor.relationship.copy(profile=ResponseProfile(60,2.0,1.0)))
        val patient=actor.copy(relationship=actor.relationship.copy(profile=ResponseProfile(720,.25,1.0)))
        val a=RelationshipRules.advance(waiting(sensitive),start+12*hour).a()
        val b=RelationshipRules.advance(waiting(patient),start+12*hour).a()
        assertTrue(a.affinity < b.affinity)
        assertTrue(a.relationship.irritation > b.relationship.irritation)
        assertEquals(60,RelationshipRules.advance(waiting(),start+365*24*hour).a().affinity)
    }

    @Test fun actualUserReplyEndsWaitingButKeepsLossAndRecoversGradually() {
        val after=RelationshipRules.received(waiting(),"a",200,start+30*hour)
        assertNull(after.a().relationship.waiting)
        assertEquals(76,after.a().affinity)
        assertTrue(after.a().relationship.irritation > 0)
        assertEquals(after,RelationshipRules.received(after,"a",200,start+31*hour))
        val later=RelationshipRules.advance(after,start+42*hour)
        assertEquals(76,later.a().affinity)
        assertEquals(after.a().relationship.anxiety/2,later.a().relationship.anxiety,.00001)
        assertTrue(later.a().relationship.contactFactor > after.a().relationship.contactFactor)
        assertNull(RelationshipRules.delivered(after,delivery(100),start+31*hour).a().relationship.waiting)
    }

    @Test fun recoveryUsesElapsedTimeIndependentlyOfTickFrequency() {
        val after=RelationshipRules.received(waiting(),"a",200,start+30*hour)
        val once=RelationshipRules.advance(after,start+42*hour)
        var many=after
        for(i in 1..24) many=RelationshipRules.advance(many,start+30*hour+i*30*GAME_MINUTE)
        assertEquals(once.a().relationship.anxiety,many.a().relationship.anxiety,.00001)
        assertEquals(once.a().relationship.irritation,many.a().relationship.irritation,.00001)
    }

    @Test fun knownAbsenceRequiresUserEvidenceAndExtendsExpectation() {
        val input=ChatMessage("user","我要出差，后天中午回来",ts=90)
        val received=RelationshipRules.received(world(),"a",90,start)
        val turn=LifeTurn(absence=AbsenceSuggestion("出差","2048-06-14T12:00:00",input.content))
        val accepted=applyLifeTurn(received,"a",turn,input.content,"好","absence",listOf(input))
        assertEquals(90L,accepted.a().relationship.absence!!.sourceMessageTs)
        val awaiting=RelationshipRules.delivered(accepted,delivery(),start)
        assertEquals(start+48*hour,awaiting.a().relationship.waiting!!.expectedReplyAt)
        assertEquals(80,RelationshipRules.advance(awaiting,start+40*hour).a().affinity)
        val denied=applyLifeTurn(received,"a",turn.copy(absence=turn.absence!!.copy(evidence="休息三天")),input.content,"好","bad",listOf(input))
        assertNull(denied.a().relationship.absence)
        val invalid=applyLifeTurn(received,"a",turn.copy(absence=turn.absence.copy(returnsAt="bad")),input.content,"好","bad2",listOf(input))
        assertNull(invalid.a().relationship.absence)
    }

    @Test fun recoverySuggestionsAreBoundedAndSameInputIsNotAppliedAgain() {
        val w=RelationshipRules.received(waiting(),"a",200,start+48*hour)
        val turn=LifeTurn(reason="用户说明原因",evidence="抱歉",anxietyDelta=-100,irritationDelta=-100)
        val updated=applyLifeTurn(w,"a",turn,"抱歉","知道了","repair")
        assertEquals(w.a().relationship.irritation-10,updated.a().relationship.irritation,.001)
        assertTrue(updated.a().relationship.irritation>0)
        assertEquals(updated,applyLifeTurn(updated,"a",turn,"抱歉","知道了","repair"))
        assertEquals(updated.a().relationship,applyLifeTurn(updated,"a",turn,"抱歉","知道了","another").a().relationship)
    }

    @Test fun modelCannotReapplyNeglectOrEraseMoodDuringUnansweredContact() {
        val w=RelationshipRules.advance(waiting(),start+30*hour)
        val turn=LifeTurn(reason="你没回",evidence="你没回",affinityDelta=-3,mood="开心",moodIntensity=0,anxietyDelta=-10)
        val updated=applyLifeTurn(w,"a",turn,"","你没回","outgoing")
        assertEquals(w.a().affinity,updated.a().affinity)
        assertEquals(w.a().mood,updated.a().mood)
        assertEquals(w.a().relationship.anxiety,updated.a().relationship.anxiety,0.0)
    }

    @Test fun profileInitializesOnceAndRejectsInvalidValues() {
        val turn=LifeTurn(responseProfile=ResponseProfile(60,2.0,.5))
        val once=applyLifeTurn(world(),"a",turn,"","你好","profile")
        val twice=applyLifeTurn(once,"a",turn.copy(responseProfile=ResponseProfile(720,.5,2.0)),"","你好","profile2")
        assertEquals(once.a().relationship.profile,twice.a().relationship.profile)
        assertEquals("model",once.a().relationship.profileSource)
        val invalid=applyLifeTurn(world(),"a",turn.copy(responseProfile=ResponseProfile(0,Double.NaN,2.0)),"","你好","invalid")
        assertEquals("default",invalid.a().relationship.profileSource)
    }

    @Test fun lowerWillingnessReducesOpportunitiesAndExtendsIntervalsDeterministically() {
        val low=actor.copy(relationship=actor.relationship.copy(contactFactor=.1))
        val high=(0..499).count { RelationshipRules.allowsContact(actor,start+it*GAME_MINUTE) }
        val few=(0..499).count { RelationshipRules.allowsContact(low,start+it*GAME_MINUTE) }
        assertTrue(few < high/2)
        assertTrue(LifeRules.nextContact(low,start)>LifeRules.nextContact(actor,start))
        assertEquals(RelationshipRules.allowsContact(low,start),RelationshipRules.allowsContact(low,start))
    }

    @Test fun clockRebasePreservesWaitAgeAndSummaryOmitsStorageMetadata() {
        val w=RelationshipRules.advance(waiting(),start+30*hour)
        val s=w.a().relationship
        val shifted=RelationshipRules.rebase(s,-3*hour)
        assertEquals(s.lastSettledAt!!-s.waiting!!.deliveredAt,shifted.lastSettledAt!!-shifted.waiting!!.deliveredAt)
        val prompt=RelationshipRules.summary(w.a(),start+30*hour)
        assertTrue(prompt.contains("今晚聊吗？"))
        assertTrue(prompt.contains("1800 分钟"))
        assertFalse(prompt.contains("sessionId"))
        assertFalse(prompt.contains("ruleVersion"))
        assertFalse(prompt.contains("messageTs"))
    }
}
