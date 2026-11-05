package me.rerere.momoly.data.world

import org.junit.Assert.*
import org.junit.Test

class WorldLifeTest {
    private val map=WorldMap(places=listOf(WorldPlace("home","家"),WorldPlace("work","值班处")),
        routes=listOf(WorldRoute("home","work",15)),userLocation="home")
    private val start=gameTime("2048-06-12T17:30:00")
    private val initial=LifeInitial(initialTime="2048-06-12T17:30:00",initialLocation="home",background="交接后休息",history="执舰官")
    private val actor=LifeActor("a","A",initial,"home","正在休息",listOf(
        LifeSchedule("work",1080,1200,"值班","work","unavailable")),50,"平静",60,nextContact=start+60*GAME_MINUTE)
    private fun world(name:String="A")=LifeWorld(name,map,start,start,actors=mapOf("a" to actor))

    @Test fun allWorldTargetsFollowRealTimeAndSwitchingPreservesPendingEvents() {
        val zone=java.time.ZoneOffset.UTC
        var u=LifeUniverse(active="A",wallAnchor=start,worlds=mapOf("A" to world(),"B" to world("B")))
        u=LifeRules.accrue(u,start+10*GAME_MINUTE,100,4,zone)
        assertTrue(u.worlds.values.all { it.target==start+10*GAME_MINUTE && it.now==start })
        u=u.copy(active="B")
        u=LifeRules.accrue(u,start+20*GAME_MINUTE,200,4,zone)
        assertTrue(u.worlds.values.all { it.target==start+20*GAME_MINUTE && it.now==start })
        assertEquals(u,LifeRules.accrue(u,start+20*GAME_MINUTE,200,4,zone))
    }
    @Test fun rebootAndWallClockCorrectionsNeverKeepAnAcceleratedTarget() {
        val zone=java.time.ZoneOffset.UTC
        val u=LifeUniverse(active="A",wallAnchor=start,worlds=mapOf("A" to world()))
        val once=LifeRules.accrue(u,start+10*GAME_MINUTE,2000,5,zone)
        assertEquals(start+10*GAME_MINUTE,once.worlds.getValue("A").target)
        val backwards=LifeRules.accrue(once,start-GAME_MINUTE,10,6,zone)
        assertEquals(start-GAME_MINUTE,backwards.worlds.getValue("A").now)
        assertEquals(backwards.worlds.getValue("A").now,backwards.worlds.getValue("A").target)
        assertTrue(backwards.clockWarning.isNotEmpty())
    }
    @Test fun phoneZoneAndZoneChangesUseLocalCalendarWithoutReplayingHistory() {
        val wall=java.time.Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()
        assertEquals("2026-11-06 20:00",gameLabel(realWorldTime(wall,java.time.ZoneId.of("Asia/Shanghai"))))
        val u=LifeUniverse(timeZone="Asia/Shanghai",worlds=mapOf("A" to world()))
        val changed=LifeRules.accrue(u,wall,0,0,java.time.ZoneId.of("America/New_York"))
        assertEquals("2026-11-06 07:00",gameLabel(changed.worlds.getValue("A").now))
        assertEquals(changed.worlds.getValue("A").now,changed.worlds.getValue("A").target)
    }
    @Test fun legacySaveCancelsFutureTimeAndPreservesHistoryAndRemainingTimers() {
        val at=gameTime("2026-11-06T20:00:00")
        val a=actor.copy(sessionId="s",pendingInput=1,travel=LifeTravel("home","work",start+15*GAME_MINUTE),
            schedule=listOf(LifeSchedule("tomorrow",600,660,"约定","home",date="2048-06-13")))
        val old=world().copy(target=start+1440*GAME_MINUTE,actors=mapOf("a" to a),messageTimes=mapOf("s:1" to start))
        val migrated=LifeRules.accrue(LifeUniverse(version=1,worlds=mapOf("A" to old)),at,0,0,java.time.ZoneOffset.UTC)
        val w=migrated.worlds.getValue("A")
        assertEquals(2,migrated.version)
        assertEquals(at,w.now);assertEquals(at,w.target)
        assertEquals(old.messageTimes,w.messageTimes)
        assertTrue(LifeRules.inputIsDue(w,w.actors.getValue("a")))
        assertFalse(LifeRules.inputIsDue(w,w.actors.getValue("a").copy(pendingAt=at+GAME_MINUTE)))
        assertEquals(at+60*GAME_MINUTE,w.actors.getValue("a").nextContact)
        assertEquals(at+15*GAME_MINUTE,w.actors.getValue("a").travel!!.arrives)
        assertEquals("2026-11-07",w.actors.getValue("a").schedule.single().date)
        assertEquals(a.affinity,w.actors.getValue("a").affinity)
        assertEquals(migrated,LifeRules.accrue(migrated,at,0,0,java.time.ZoneOffset.UTC))
    }
    @Test fun fiveMinuteBoundaryAndMidnightLabelsUseGameTime() {
        assertNotNull(gameMessageLabel(null,start))
        assertNull(gameMessageLabel(start,start+299999))
        assertEquals("17:35",gameMessageLabel(start,start+300000))
        assertEquals("17:35",gameMessageLabel(start,start+300001))
        assertEquals("2048-06-13 00:00",gameMessageLabel(gameTime("2048-06-12T23:59:00"),gameTime("2048-06-13T00:00:00")))
    }
    @Test fun intervalStartsWithTravelAndArrivalTakesTheDeclaredTime() {
        var w=world().copy(target=start+120*GAME_MINUTE)
        w=LifeRules.advance(w,start+30*GAME_MINUTE)
        assertEquals("home",w.actors.getValue("a").location)
        assertEquals("work",w.actors.getValue("a").travel!!.to)
        assertEquals("brief",LifeRules.availability(w.actors.getValue("a"),w.now))
        w=LifeRules.advance(w,start+45*GAME_MINUTE)
        assertNull(w.actors.getValue("a").travel)
        assertEquals("work",w.actors.getValue("a").location)
        assertEquals("unavailable",LifeRules.availability(w.actors.getValue("a"),w.now))
    }
    @Test fun rejectsInvalidMapsAndOverlappingIntervals() {
        assertThrows(IllegalArgumentException::class.java) { map.copy(userLocation="missing").validate() }
        assertThrows(IllegalArgumentException::class.java) { map.copy(places=listOf(map.places[0],map.places[0])).validate() }
        assertThrows(IllegalArgumentException::class.java) { initial.copy(initialLocation="missing").validate(map) }
        assertThrows(IllegalArgumentException::class.java) { validateSchedules(listOf(
            LifeSchedule("a",0,60,"one","home"),LifeSchedule("b",59,100,"two","home")),map) }
    }
    @Test fun timeWeatherAndDatedAppointmentsAreDeterministic() {
        val dated=LifeSchedule("leave",0,1440,"休假","home",date="2048-06-13")
        val a=actor.copy(schedule=actor.schedule+dated)
        assertEquals("leave",LifeRules.activity(a,gameTime("2048-06-13T18:10:00"))!!.id)
        assertEquals("work",LifeRules.activity(a,gameTime("2048-06-14T18:10:00"))!!.id)
        assertEquals(LifeRules.weather(world(),"home"),LifeRules.weather(world(),"work"))
    }
    @Test fun fixedAndPublicFactsCannotBeRewrittenByPrivateConversation() {
        val w=world().copy(facts=listOf(LifeFact("fixed","固定","A",scope="a"),
            LifeFact("public","公开","B",mutable=true),LifeFact("private","私有","C",mutable=true,scope="a")))
        val turn=LifeTurn(listOf("我答应了"),affinityDelta=2,reason="达成约定",evidence="明晚聊",
            facts=listOf("fixed","public","private").map { LifeFactChange(it,"已约定","我答应了") })
        val result=applyLifeTurn(w,"a",turn,"明晚聊","我答应了","e1")
        assertEquals(listOf("A","B","已约定"),result.facts.map { it.content })
        assertEquals(52,result.actors.getValue("a").affinity)
        assertEquals(2,result.revisions.size)
        val noEvidence=applyLifeTurn(w,"a",turn.copy(evidence="凭空"),"明晚聊","","e2")
        assertEquals(50,noEvidence.actors.getValue("a").affinity)
        assertEquals(w.facts,noEvidence.facts)
    }
    @Test fun publicWeatherEntriesFollowActualEventsAndKeepRevisionTrail() {
        val w=world().copy(target=start+30*GAME_MINUTE,facts=listOf(
            LifeFact("weather","天气","尚未观测",mutable=true,binding="weather:home")))
        val updated=LifeRules.advance(w,w.target)
        assertEquals(LifeRules.weather(updated,"home"),updated.facts.single().content)
        assertEquals("尚未观测",updated.revisions.single().before)
        assertEquals(updated,LifeRules.advance(updated,updated.now))
    }
    @Test fun proposalCannotCreateAppointmentWithoutDeliveredAgreement() {
        val future=LifeSchedule("call",1200,1230,"聊天","home",date="2048-06-12")
        val turn=LifeTurn(listOf("再看看"),reason="约定",evidence="晚上聊",addSchedules=listOf(future))
        val w=world()
        val proposed=applyLifeTurn(w,"a",turn,"晚上聊","再看看","proposal")
        assertEquals(w.actors.getValue("a").schedule,proposed.actors.getValue("a").schedule)
        val agreed=applyLifeTurn(w,"a",turn.copy(agreementEvidence="好，八点聊"),"晚上聊","好，八点聊","agreement")
        assertTrue(agreed.actors.getValue("a").schedule.any { it.id=="call" })
    }
}
