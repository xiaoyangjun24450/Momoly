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
        val r=WorldLifeRuntime(context,repo,config,gateway,extensions,typingDelay={})
        runtime=r;r.syncCards()
        val name=WorldLifeStore.read(context).worlds.keys.single()
        r.switchWorld(name)
        return r
    }
    private suspend fun waitFor(condition:()->Boolean) {
        try { withTimeout(15_000) { while(!condition()) delay(20) } }
        catch (e:TimeoutCancellationException) {
            val w=WorldLifeStore.read(context).worlds.values.firstOrNull()
            throw AssertionError("status=${runtime?.status?.value}; time=${w?.now}/${w?.target}; rest=${w?.restUntil}; actors=${w?.actors?.values?.map{Triple(it.pendingInput,it.answeredInput,it.activeSchedule)}}",e)
        }
    }

    @Test fun suppliedPngRestPublishesMultipleHistoricalMessagesInOneSession()=runBlocking {
        val prompts=mutableListOf<String>()
        val r=create(GenerationGateway { request,emit ->
            prompts+=request.messages.last().content
            emit(GenerationEvent.ContentDelta("""{"messages":["这会儿看到了终端","晚一点再聊"]}"""));StreamEnd()
        })
        r.foreground(true);r.rest(240)
        waitFor { val u=WorldLifeStore.read(context);u.worlds[u.active]?.restUntil==null && !r.status.value.working }
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
        assertTrue(prompts.all { it.contains("没有新的用户消息") && it.contains("用户正在休息") })
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
        r.foreground(true);r.rest(60)
        waitFor { WorldLifeStore.read(context).worlds.values.single().restUntil==null && !r.status.value.working }
        r.close()
        assertEquals(2,requests.size)
        assertEquals(2,requests.last().messages.size)
        val messages=repo.get(repo.listSummaries().single().id)!!.messages
        assertEquals(1,messages.count { it.content=="交接好了" }) // Travelling permits only a brief first segment.
        assertTrue(messages.none { "moodIntensity" in it.content })
        assertEquals("轻松",WorldLifeStore.read(context).worlds.values.single().actors.values.single().mood)
    }
    @Test fun failedGenerationKeepsCursorAndResumeDoesNotPublishTwice()=runBlocking {
        var failures=1
        val r=create(GenerationGateway { _,emit ->
            if(failures-->0) error("模拟断网")
            emit(GenerationEvent.ContentDelta("""{"messages":["现在可以联系了"]}"""));StreamEnd()
        })
        r.foreground(true);r.rest(60)
        waitFor { r.status.value.paused }
        val failed=WorldLifeStore.read(context).worlds.values.single()
        assertNotNull(failed.restUntil)
        assertTrue(failed.now<failed.target)
        assertTrue(failed.outbox.isEmpty())
        r.resume()
        waitFor { WorldLifeStore.read(context).worlds.values.single().restUntil==null && !r.status.value.working }
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
        r.foreground(true);r.rest(60)
        withTimeout(15000){started.await()}
        r.switchWorld("Other");release.complete(Unit);delay(100)
        r.foreground(false)
        assertEquals("Other",WorldLifeStore.read(context).active)
        for(s in repo.listSummaries()) assertTrue(repo.get(s.id)!!.messages.none { it.content=="过期结果" })
    }
    @Test fun busyContactKeepsTwoInputsAndAnswersOnceWhenFree()=runBlocking {
        val requests=mutableListOf<GenerationStreamRequest>()
        val r=create(GenerationGateway { request,emit ->
            requests+=request
            emit(GenerationEvent.ContentDelta("""{"messages":["现在忙完了，两件事我都看到了"]}"""));StreamEnd()
        })
        val w=WorldLifeStore.read(context).worlds.values.single();val a=w.actors.values.single()
        WorldLifeStore.world(context,w.name) { world ->world.copy(actors=world.actors+(a.file to a.copy(schedule=listOf(
            LifeSchedule("busy",0,1080,"暂不能看终端","supply","unavailable"),
            LifeSchedule("free",1080,1440,"休息","supply","free")),nextContact=world.now+1440*GAME_MINUTE))) }
        val card=CharacterRepository.load(context,a.file)
        val session=me.rerere.momoly.domain.chat.ChatSessionCoordinator(repo).forCharacter(card,a.file,a.name)
        val ts=System.currentTimeMillis()+10
        val user1=ChatMessage("user","第一件事",ts=ts)
        val user2=ChatMessage("user","第二件事",ts=ts+1)
        repo.putMessage(session.id,user1);repo.putMessage(session.id,user2)
        WorldLifeStore.world(context,w.name) { world -> val actor=world.actors.getValue(a.file)
            world.copy(actors=world.actors+(a.file to actor.copy(sessionId=session.id,pendingInput=ts+1,inputReadyAt=0))) }
        r.foreground(true)
        delay(200)
        assertTrue(requests.isEmpty())
        r.rest(60)
        waitFor { WorldLifeStore.read(context).worlds.values.single().restUntil==null && !r.status.value.working }
        r.close()
        assertEquals(1,requests.size)
        assertTrue(requests.single().messages.any { "第一件事" in it.content })
        assertTrue(requests.single().messages.any { "第二件事" in it.content })
        val current=WorldLifeStore.read(context).worlds.values.single()
        assertEquals(ts+1,current.actors.values.single().answeredInput)
        assertEquals(2,repo.get(session.id)!!.messages.count { it.role=="user" })
    }
    @Test fun inputDuringCatchUpStaysInEditorUntilHistoryIsPublished()=runBlocking {
        val r=create(GenerationGateway { _,_ -> error("Must not generate while stopped") })
        val actor=WorldLifeStore.read(context).worlds.values.single().actors.values.single()
        val card=CharacterRepository.load(context,actor.file)
        r.rest(60)
        var appended=false
        assertTrue(r.submit("pending",card,config.load().providers.first(),"test") { appended=true;true })
        assertFalse(appended)
        assertTrue(r.status.value.error.orEmpty().contains("输入内容已保留"))
    }
    @Test fun twoCharactersCrossMidnightWithoutMixingSessionsOrPrivateFacts()=runBlocking {
        val original=File(CharacterRepository.charsDir(context),CharacterRepository.listNames(context).single()+".json")
        val copy=File(context.cacheDir,"second-life-card.json").apply { writeText(original.readText()) }
        CharacterRepository.import(context,Uri.fromFile(copy))
        val r=create(GenerationGateway { _,emit ->
            emit(GenerationEvent.ContentDelta("""{"messages":["今晚的事情处理完了"]}"""));StreamEnd()
        })
        r.foreground(true);r.rest(480)
        waitFor { WorldLifeStore.read(context).worlds.values.single().restUntil==null && !r.status.value.working }
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
        val out=LifeDelivery(session.id,message,w.now)
        WorldLifeStore.world(context,w.name) { it.copy(outbox=listOf(out),messageTimes=it.messageTimes+("${session.id}:${message.ts}" to w.now)) }
        repo.putMessage(session.id,message) // Simulate death between Room write and outbox acknowledgement.
        r.foreground(true)
        waitFor { WorldLifeStore.read(context).worlds.values.single().outbox.isEmpty() }
        r.close()
        assertEquals(1,repo.get(session.id)!!.messages.count { it.ts==message.ts })
        assertEquals(a.affinity,WorldLifeStore.read(context).worlds.values.single().actors.values.single().affinity)
    }
}
