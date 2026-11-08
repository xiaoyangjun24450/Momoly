package me.rerere.momoly.ui.world

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import me.rerere.momoly.data.api.*
import me.rerere.momoly.data.character.CharacterRepository
import me.rerere.momoly.data.chat.RoomChatDataRepository
import me.rerere.momoly.data.chat.ChatMessage
import me.rerere.momoly.data.settings.WorldLifeStore
import me.rerere.momoly.data.world.*
import me.rerere.momoly.domain.*
import me.rerere.momoly.extension.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class WorldLifeRuntimeTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val repo=LifeChatRepository(context,RoomChatDataRepository(context))
    private val config=object:ApiConfigRepository {
        override fun load()=ApiConfig(listOf(ApiProvider(id="test",name="test",models=listOf(ModelInfo("test")))))
        override fun save(config:ApiConfig) { }
    }
    private val extensions=object:ExtensionGateway {
        override fun enabledExtensions()=emptyList<Extension>()
        override fun config(id:String)=""
        override fun services(apiConfig:ApiConfig):ExtensionServices=error("No extensions in this test")
    }
    private var runtime:WorldLifeRuntime?=null
    private var clock=gameTime("2048-06-12T17:30:00")
    private fun wallClock()=gameDate(clock).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun elapse(minutes: Int) { clock+=minutes*GAME_MINUTE;runtime!!.resume() }
    private fun caughtUp(): Boolean {
        val w=WorldLifeStore.read(context).worlds.values.first()
        return w.now>=clock && !runtime!!.status.value.working
    }
    @Before fun setup()=runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context.filesDir.listFiles()?.filter { it.name in listOf("characters","worldbooks","regex","presets") }?.forEach { it.deleteRecursively() }
        File(context.filesDir,"world-life-v1.json").delete()
        WorldLifeStore.forgetCacheForTests()
        repo.listSummaries().forEach { repo.delete(it.id) }
        context.getSharedPreferences("home_world",0).edit().clear().commit()
        val root=generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it,"artifacts/phase-02/夏以昼-阶段二.png").isFile }
        CharacterRepository.import(context,Uri.fromFile(File(root,"artifacts/phase-02/夏以昼-阶段二.png")))
        Unit
    }
    @After fun tearDown() { runBlocking { runtime?.close() };Dispatchers.resetMain() }
    private suspend fun create(gateway:GenerationGateway):WorldLifeRuntime {
        val r=WorldLifeRuntime(context,repo,config,gateway,extensions,typingDelay={},wallClock=::wallClock)
        runtime=r;r.syncCards()
        val name=WorldLifeStore.read(context).worlds.keys.single()
        r.switchWorld(name)
        return r
    }
    private suspend fun waitFor(condition:()->Boolean) {
        try { withTimeout(15_000) { while(!condition()) delay(20) } }
        catch (e:TimeoutCancellationException) {
            val w=WorldLifeStore.read(context).worlds.values.firstOrNull()
            throw AssertionError("status=${runtime?.status?.value}; time=${w?.now}/${w?.target}; actors=${w?.actors?.values?.map{Triple(it.pendingInput,it.answeredInput,it.activeSchedule)}}",e)
        }
    }

    @Test fun suppliedPngOpeningIsPersistedOnceWhenChatIsOpened()=runBlocking {
        val file=CharacterRepository.listNames(context).single()
        val card=CharacterRepository.load(context,file)
        assertEquals("小{{user}}，明天我休假，准备回你那里一趟。",card.firstMes)
        val coordinator=me.rerere.momoly.domain.chat.ChatSessionCoordinator(repo)
        val first=coordinator.forCharacter(card,file,card.name)
        val reopened=coordinator.forCharacter(card,file,card.name)
        assertEquals(first.id,reopened.id)
        val messages=repo.get(first.id)!!.messages
        assertEquals(1,messages.size)
        assertEquals("assistant",messages.single().role)
        assertEquals(card.firstMes,messages.single().content)
    }

    @Test fun generationKeepsAuthoredScenarioAlongsideCurrentWorldState()=runBlocking {
        val file=File(CharacterRepository.charsDir(context),"${CharacterRepository.listNames(context).single()}.json")
        val json=org.json.JSONObject(file.readText())
        val scenario="你们使用专属频道联系，频道暗号是蓝色纸鹤。"
        json.getJSONObject("data").put("scenario",scenario)
        file.writeText(json.toString())
        val requests=mutableListOf<GenerationStreamRequest>()
        val r=create(GenerationGateway { request,emit ->
            requests+=request
            emit(GenerationEvent.ContentDelta("""{"messages":[]}"""));StreamEnd()
        })
        r.foreground(true);elapse(60)
        waitFor { caughtUp() };r.foreground(false)
        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { request -> request.messages.any { scenario in it.content } })
        assertTrue(requests.all { "年月日使用此处世界日期" in it.messages.last().content })
    }

    @Test fun suppliedPngOfflineCatchUpPublishesMultipleHistoricalMessagesInOneSession()=runBlocking {
        val prompts=mutableListOf<String>()
        val r=create(GenerationGateway { request,emit ->
            prompts+=request.messages.last().content
            emit(GenerationEvent.ContentDelta("""{"messages":["这会儿看到了终端","晚一点再聊"]}"""));StreamEnd()
        })
        r.foreground(true);elapse(240)
        waitFor { caughtUp() }
        r.foreground(false)
        assertFalse(r.status.value.error.orEmpty(),r.status.value.paused)
        val sessions=repo.listSummaries();assertEquals(1,sessions.size)
        val messages=repo.get(sessions.single().id)!!.messages
        assertTrue("messages=${messages.map{it.content}} prompts=${prompts.size} status=${r.status.value}",messages.size>=3)
        assertTrue(messages.none { it.role=="user" })
        val w=WorldLifeStore.read(context).worlds.values.single()
        assertTrue(w.outbox.isEmpty())
        val times=messages.mapNotNull { w.messageTimes["${sessions.single().id}:${it.ts}"] }
        assertTrue(times.size>=3);assertTrue(times.distinct().size>=2)
        assertEquals(messages.size,messages.map { it.ts }.distinct().size)
        assertTrue(prompts.size>=2)
        assertTrue(prompts.all { it.contains("没有新的用户消息") && !it.contains("用户正在休息") })
        assertTrue(w.facts.any { it.revision>0 })
    }
    @Test fun proseOnlyPresetUsesSeparateStateAssessmentBeforePublishing()=runBlocking {
        val requests=mutableListOf<GenerationStreamRequest>()
        val r=create(GenerationGateway { request,emit ->
            requests+=request
            emit(GenerationEvent.ContentDelta(if(request.messages.first().content.contains("游戏状态记录器"))
                """{"mood":"轻松","moodIntensity":35,"reason":"已交接完任务","evidence":"交接好了"}""" else "交接好了，等我回去。"))
            StreamEnd()
        })
        r.foreground(true);elapse(60)
        waitFor { caughtUp() }
        r.close()
        assertEquals(2,requests.size)
        assertEquals(2,requests.last().messages.size)
        val assessment=org.json.JSONObject(requests.last().messages.last().content)
        assertTrue(assessment.has("relationshipSummary"))
        assertFalse(assessment.getJSONObject("current").has("sessionId"))
        assertFalse(assessment.getJSONObject("current").has("initial"))
        assertFalse(assessment.getJSONObject("current").toString().contains("availability"))
        val messages=repo.get(repo.listSummaries().single().id)!!.messages
        assertEquals(1,messages.count { it.content=="交接好了" })
        assertEquals(1,messages.count { it.content=="等我回去" })
        assertTrue(messages.none { "moodIntensity" in it.content })
        assertEquals("轻松",WorldLifeStore.read(context).worlds.values.single().actors.values.single().mood)
    }
    @Test fun failedGenerationKeepsCursorAndResumeDoesNotPublishTwice()=runBlocking {
        var failures=1
        val r=create(GenerationGateway { _,emit ->
            if(failures-->0) error("模拟断网")
            emit(GenerationEvent.ContentDelta("""{"messages":["现在可以联系了"]}"""));StreamEnd()
        })
        r.foreground(true);elapse(60)
        waitFor { r.status.value.paused }
        val failed=WorldLifeStore.read(context).worlds.values.single()
        assertTrue(failed.now<failed.target)
        assertTrue(failed.outbox.isEmpty())
        r.resume()
        waitFor { caughtUp() }
        r.foreground(false)
        val sessions=repo.listSummaries()
        val messages=repo.get(sessions.single().id)!!.messages
        assertEquals(1,messages.count { it.content=="现在可以联系了" })
        r.close()
        val saved=WorldLifeStore.read(context)
        val disk=lifeJson.decodeFromString<LifeUniverse>(File(context.filesDir,"world-life-v1.json").readText())
        assertEquals("disk sequence",saved.worlds.values.single().sequence,disk.worlds.values.single().sequence)
        WorldLifeStore.forgetCacheForTests()
        assertEquals(saved,WorldLifeStore.read(context))
    }
    @Test fun switchingWorldDuringGenerationDiscardsOldResult()=runBlocking {
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val r=create(GenerationGateway { _,emit ->
            started.complete(Unit);release.await()
            emit(GenerationEvent.ContentDelta("""{"messages":["过期结果"]}"""));StreamEnd()
        })
        val original=WorldLifeStore.read(context).worlds.values.single()
        WorldLifeStore.update(context) { it.copy(worlds=it.worlds+("Other" to original.copy(name="Other",actors=emptyMap()))) }
        r.foreground(true);elapse(60)
        withTimeout(15000){started.await()}
        r.switchWorld("Other");release.complete(Unit);delay(100)
        r.foreground(false)
        assertEquals("Other",WorldLifeStore.read(context).active)
        for(s in repo.listSummaries()) assertTrue(repo.get(s.id)!!.messages.none { it.content=="过期结果" })
    }
    @Test fun unavailableScheduleAnswersWithoutWaitingForFreeTime()=assertUnrestrictedReply("unavailable")
    @Test fun briefSchedulePublishesAllSegmentsWithout240CharacterTruncation()=assertUnrestrictedReply("brief")
    @Test fun travellingCharacterPublishesFullReplyBeforeArrival()=assertUnrestrictedReply("free",travelling=true)

    private fun assertUnrestrictedReply(legacyAvailability: String, travelling: Boolean=false)=runBlocking {
        val requests=mutableListOf<GenerationStreamRequest>()
        val longReply="收到".repeat(160)
        val r=create(GenerationGateway { request,emit ->
            requests+=request
            emit(GenerationEvent.ContentDelta("""{"messages":["$longReply","两件事我都看到了"]}"""));StreamEnd()
        })
        val w=WorldLifeStore.read(context).worlds.values.single();val a=w.actors.values.single()
        val schedule=lifeJson.decodeFromString<LifeSchedule>("""{"id":"busy","start":0,"end":1440,"activity":"值班","location":"supply","availability":"$legacyAvailability"}""")
        WorldLifeStore.world(context,w.name) { world ->world.copy(actors=world.actors+(a.file to a.copy(
            schedule=listOf(schedule),travel=if(travelling) LifeTravel(a.location,"supply",world.now+60*GAME_MINUTE) else null,
            nextContact=world.now+1440*GAME_MINUTE))) }
        val card=CharacterRepository.load(context,a.file)
        val session=me.rerere.momoly.domain.chat.ChatSessionCoordinator(repo).forCharacter(card,a.file,a.name)
        val ts=System.currentTimeMillis()+10
        val user1=ChatMessage("user","第一件事",ts=ts)
        val user2=ChatMessage("user","第二件事",ts=ts+1)
        repo.putMessage(session.id,user1);repo.putMessage(session.id,user2)
        WorldLifeStore.world(context,w.name) { world -> val actor=world.actors.getValue(a.file)
            world.copy(actors=world.actors+(a.file to actor.copy(sessionId=session.id,pendingInput=ts+1,inputReadyAt=0))) }
        r.foreground(true)
        waitFor { WorldLifeStore.read(context).worlds.values.single().actors.values.single().answeredInput==ts+1 }
        r.close()
        assertEquals(1,requests.size)
        assertTrue(requests.single().messages.any { "第一件事" in it.content })
        assertTrue(requests.single().messages.any { "第二件事" in it.content })
        val current=WorldLifeStore.read(context).worlds.values.single()
        assertEquals(w.now,current.now)
        assertEquals(ts+1,current.actors.values.single().answeredInput)
        val messages=repo.get(session.id)!!.messages
        assertEquals(2,messages.count { it.role=="user" })
        assertEquals(1,messages.count { it.content==longReply })
        assertEquals(1,messages.count { it.content=="两件事我都看到了" })
        if(travelling) assertNotNull(current.actors.values.single().travel)
        val prompt=requests.single().messages.last().content
        assertTrue("必须返回非空的聊天正文" in prompt)
        assertFalse("通信状态" in prompt)
        assertFalse("availability" in prompt)
    }
    @Test fun inputDuringCatchUpStaysInEditorUntilHistoryIsPublished()=runBlocking {
        val r=create(GenerationGateway { _,_ -> error("Must not generate while stopped") })
        val actor=WorldLifeStore.read(context).worlds.values.single().actors.values.single()
        val card=CharacterRepository.load(context,actor.file)
        elapse(60)
        var appended=false
        assertTrue(r.submit("pending",card,config.load().providers.first(),"test") { appended=true;true })
        assertFalse(appended)
        assertNull(r.status.value.error)
    }
    @Test fun twoCharactersCrossMidnightWithoutMixingSessionsOrPrivateFacts()=runBlocking {
        val original=File(CharacterRepository.charsDir(context),CharacterRepository.listNames(context).single()+".json")
        val copy=File(context.cacheDir,"second-life-card.json").apply { writeText(original.readText()) }
        CharacterRepository.import(context,Uri.fromFile(copy))
        val r=create(GenerationGateway { _,emit ->
            emit(GenerationEvent.ContentDelta("""{"messages":["今晚的事情处理完了"]}"""));StreamEnd()
        })
        r.foreground(true);elapse(480)
        waitFor { caughtUp() }
        r.close()
        val w=WorldLifeStore.read(context).worlds.values.single()
        assertEquals("2048-06-13",gameDate(w.now).toLocalDate().toString())
        assertEquals(2,w.actors.size)
        assertEquals(2,repo.listSummaries().size)
        assertEquals(2,w.actors.values.map { it.sessionId }.distinct().size)
        assertTrue(w.actors.values.all { it.location=="quarters" && it.travel==null })
        assertTrue(w.outbox.isEmpty())
        for(s in repo.listSummaries()) {
            assertTrue(repo.get(s.id)!!.messages.none { it.role=="user" })
            assertTrue(w.messageTimes.keys.any { it.startsWith(s.id+":") })
        }
        assertTrue(w.facts.filter { it.scope!="public" }.groupBy { it.scope }.size==2)
    }
    @Test fun replayingCommittedOutboxDoesNotDuplicateMessageOrState()=runBlocking {
        val r=create(GenerationGateway { _,_ -> error("No generation should be needed") })
        val w=WorldLifeStore.read(context).worlds.values.single();val a=w.actors.values.single()
        val card=CharacterRepository.load(context,a.file)
        val session=me.rerere.momoly.domain.chat.ChatSessionCoordinator(repo).forCharacter(card,a.file,a.name)
        val message=ChatMessage("assistant","已提交但尚未清除的发件记录",ts=System.currentTimeMillis()+100)
        val out=LifeDelivery(session.id,message,w.now,replyEvidence=message.content,startsContact=true,proactive=true)
        WorldLifeStore.world(context,w.name) { it.copy(outbox=listOf(out),
            actors=it.actors+(a.file to a.copy(sessionId=session.id)),
            messageTimes=it.messageTimes+("${session.id}:${message.ts}" to w.now)) }
        repo.putMessage(session.id,message) // Simulate death between Room write and outbox acknowledgement.
        r.foreground(true)
        waitFor { WorldLifeStore.read(context).worlds.values.single().outbox.isEmpty() }
        r.close()
        assertEquals(1,repo.get(session.id)!!.messages.count { it.ts==message.ts })
        assertEquals(a.affinity,WorldLifeStore.read(context).worlds.values.single().actors.values.single().affinity)
        assertEquals(1,WorldLifeStore.read(context).worlds.values.single().actors.values.single().relationship.waiting!!.unansweredContacts)
    }

    @Test fun historicalQuestionsStartWaitingAtDeliveryAndRequestsCarryOnlyCurrentSummary()=runBlocking {
        val requests=mutableListOf<GenerationStreamRequest>()
        val r=create(GenerationGateway { request,emit ->
            requests+=request
            emit(GenerationEvent.ContentDelta("""{"messages":["今晚聊吗？"],"expectsReply":true,"replyEvidence":"今晚聊吗？","responseProfile":{"patienceMinutes":60,"sensitivity":1.5,"recoverySpeed":0.5}}"""))
            StreamEnd()
        })
        val before=WorldLifeStore.read(context).worlds.values.single().actors.values.single().affinity
        r.foreground(true);elapse(240)
        waitFor { caughtUp() };r.foreground(false)
        val a=WorldLifeStore.read(context).worlds.values.single().actors.values.single()
        assertEquals(before,a.affinity)
        assertEquals(clock,a.relationship.waiting!!.deliveredAt)
        assertEquals(clock+60*GAME_MINUTE,a.relationship.waiting!!.expectedReplyAt)
        assertEquals("model",a.relationship.profileSource)
        assertTrue(requests.all { "【当前关系与等待状态】" in it.messages.last().content })
        assertTrue(requests.all { "ruleVersion" !in it.messages.last().content })
    }

    @Test fun silenceSettlesBeforeTheNextContactOpportunity()=runBlocking {
        var requests=0
        val r=create(GenerationGateway { _,_ -> requests++;error("No model call expected") })
        val w=WorldLifeStore.read(context).worlds.values.single()
        val a=w.actors.values.single()
        WorldLifeStore.world(context,w.name) { current ->
            val wait=ReplyWait("s",100,"在吗？",current.now,current.now+60*GAME_MINUTE,1)
            current.copy(actors=current.actors+(a.file to a.copy(
                schedule=listOf(LifeSchedule("busy",0,1440,"值班",a.location)),
                nextContact=current.now+24*60*GAME_MINUTE,
                relationship=LifeRelationship(profileSource="card",waiting=wait,lastSettledAt=current.now)) ))
        }
        r.foreground(true);elapse(8*60)
        waitFor { caughtUp() };r.foreground(false)
        val after=WorldLifeStore.read(context).worlds.values.single().actors.values.single()
        assertEquals(0,requests)
        assertTrue(after.affinity<a.affinity)
        assertTrue(after.relationship.anxiety>0)
        assertEquals("irritated",after.relationship.stage)
    }

    @Test fun userReplyBypassesLowContactWillingnessAndKeepsExistingHurt()=runBlocking {
        val prompts=mutableListOf<String>()
        val r=create(GenerationGateway { request,emit ->
            prompts+=request.messages.last().content
            emit(GenerationEvent.ContentDelta("""{"messages":["我看到了"]}"""));StreamEnd()
        })
        val w=WorldLifeStore.read(context).worlds.values.single();val a=w.actors.values.single()
        val card=CharacterRepository.load(context,a.file)
        val session=me.rerere.momoly.domain.chat.ChatSessionCoordinator(repo).forCharacter(card,a.file,a.name)
        WorldLifeStore.world(context,w.name) { current -> current.copy(actors=current.actors+(a.file to a.copy(
            sessionId=session.id,nextContact=current.now+24*60*GAME_MINUTE,
            schedule=listOf(LifeSchedule("free",0,1440,"休息",a.location)),
            relationship=LifeRelationship(profileSource="card",anxiety=70.0,irritation=80.0,contactFactor=.05,lastSettledAt=current.now,
                waiting=ReplyWait(session.id,100,"在吗？",current.now,current.now+60*GAME_MINUTE,3,70.0,80.0))))) }
        val input=ChatMessage("user","我回来了",ts=System.currentTimeMillis())
        r.foreground(true)
        r.submit(session.id,card,config.load().providers.single(),"test") { repo.putMessage(session.id,input);true }
        waitFor { WorldLifeStore.read(context).worlds.values.single().actors.values.single().relationship.lastUserMessageTs==input.ts }
        // 测试调度器不会随墙钟推进输入收集计时，此处只跳过已经单独覆盖的三秒窗口。
        WorldLifeStore.world(context,w.name) { current -> val ready=current.actors.getValue(a.file)
            current.copy(actors=current.actors+(a.file to ready.copy(inputReadyAt=0))) }
        r.resume()
        waitFor { WorldLifeStore.read(context).worlds.values.single().actors.values.single().answeredInput==input.ts }
        r.foreground(false)
        val after=WorldLifeStore.read(context).worlds.values.single().actors.values.single()
        assertNull(after.relationship.waiting)
        assertTrue(after.relationship.irritation>70)
        assertTrue(prompts.any { "现在处理用户尚未得到答复的留言" in it && "目前没有等待用户回应" in it })
    }
}
