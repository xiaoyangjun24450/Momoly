package me.rerere.momoly.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.rerere.momoly.data.world.*
import me.rerere.momoly.ui.theme.weChatColors

@Composable internal fun WorldMapPage(world: LifeWorld?, onProfile: (String) -> Unit = {}) {
    val colors=weChatColors()
    CompositionLocalProvider(LocalContentColor provides colors.incomingText) {
    if(world==null) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("添加带地图的角色后，这里会显示世界位置。") }; return@CompositionLocalProvider }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("world_map"),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(world.name,style=MaterialTheme.typography.titleLarge)
        Text("${gameLabel(world.now)} · ${LifeRules.weather(world,world.map.userLocation)}")
        val accent=colors.sendButton
        val line=colors.secondaryText.copy(alpha=.5f)
        BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp).background(colors.listBackground)) {
            val width=maxWidth; val height=maxHeight
            Canvas(Modifier.fillMaxSize()) {
                fun point(p: WorldPlace)=Offset(24.dp.toPx()+p.x*(size.width-48.dp.toPx()),24.dp.toPx()+p.y*(size.height-48.dp.toPx()))
                world.map.routes.forEach { r ->
                    val a=world.map.places.first { it.id==r.from }; val b=world.map.places.first { it.id==r.to }
                    drawLine(line,point(a),point(b),3.dp.toPx())
                }
                world.map.places.forEach { drawCircle(accent,5.dp.toPx(),point(it)) }
                drawCircle(Color(0xFF25B86B),9.dp.toPx(),point(world.map.places.first { it.id==world.map.userLocation }))
                world.actors.values.forEach { a ->
                    val p=world.map.places.first { it.id==a.location }
                    drawCircle(Color(0xFFE3A84F),7.dp.toPx(),point(p))
                }
            }
            world.map.places.forEach { place ->
                val people=buildList { if(place.id==world.map.userLocation) add("你");world.actors.values.filter { it.location==place.id }.forEach { add(it.name+if(it.travel!=null)"（途中）" else "") } }
                Text(place.name+if(people.isEmpty()) "" else "\n"+people.joinToString("、"),
                    Modifier.offset(x=12.dp+(width-105.dp)*place.x,y=32.dp+(height-48.dp)*place.y)
                    .widthIn(max=100.dp).background(colors.listBackground.copy(alpha=.9f)).padding(3.dp),fontSize=11.sp)
            }
        }
        Text("你 · ${world.map.name(world.map.userLocation)}",fontWeight=FontWeight.SemiBold)
        world.actors.values.forEach { actor ->
            OutlinedCard(onClick={onProfile(actor.file)},modifier=Modifier.fillMaxWidth(),
                colors=CardDefaults.outlinedCardColors(containerColor=colors.listBackground,contentColor=colors.incomingText)) {
                Column(Modifier.padding(12.dp)) {
                    Text(actor.name,fontWeight=FontWeight.SemiBold)
                    Text(actor.travel?.let { "${world.map.name(it.from)} → ${world.map.name(it.to)} · ${gameLabel(it.arrives,false)} 到达" }
                        ?:world.map.name(actor.location))
                    Text(LifeRules.weather(world,actor.location),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("路线耗时",style=MaterialTheme.typography.titleSmall)
        world.map.routes.forEach { Text("${world.map.name(it.from)} ↔ ${world.map.name(it.to)} · ${it.minutes} 分钟",fontSize=12.sp) }
    }
    }
}

@Composable internal fun CharacterLifeProfile(world: LifeWorld?, file: String, onClose: () -> Unit) {
    val actor=world?.actors?.get(file)
    var appraisal by remember(file) { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onClose,
        title={Text(if(appraisal) "鉴定 · ${actor?.name ?: file}" else actor?.name ?: file)},
        text={Column(Modifier.verticalScroll(rememberScrollState()).testTag("character_life_profile"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(actor==null) Text("这个角色没有阶段二初始信息，请导入改造后的角色卡。")
            else if(!appraisal) {
                Text("前尘往事",fontWeight=FontWeight.SemiBold)
                Text(actor.initial.history)
                OutlinedButton(onClick={appraisal=true},modifier=Modifier.testTag("open_appraisal")) { Text("鉴定") }
            } else {
                Text("此时此刻",fontWeight=FontWeight.SemiBold)
                Text(LifeRules.situation(world,actor))
                Text("心情：${actor.mood} · ${actor.moodIntensity}/100")
                Text("好感度：${actor.affinity}/100")
                Text("真实日程",fontWeight=FontWeight.SemiBold)
                if(actor.schedule.isEmpty()) Text("当前自主安排，没有固定日程。")
                actor.schedule.sortedWith(compareBy<LifeSchedule>{it.date}.thenBy{it.start}).forEach { s ->
                    fun hhmm(m:Int)="%02d:%02d".format(m/60,m%60)
                    val availability=when(s.availability) { "unavailable"->"暂不回复";"brief"->"只能简短回复";else->"可正常聊天" }
                    Text("${s.date.ifBlank{"每天"}} ${hhmm(s.start)}—${hhmm(s.end)}\n${s.activity} · ${world.map.name(s.location)}\n$availability",fontSize=13.sp)
                }
                if(actor.pendingInput>actor.answeredInput) Text("有尚待回复的留言。")
            }
        }},confirmButton={TextButton(onClick=onClose){Text("关闭")}},
        dismissButton={if(appraisal) TextButton(onClick={appraisal=false}){Text("返回资料")}})
}

@Composable internal fun WorldLifeControls(runtime: WorldLifeRuntime, world: LifeWorld?) {
    val status by runtime.status.collectAsState()
    val universe by runtime.worlds.collectAsState()
    val colors=weChatColors()
    var choose by remember { mutableStateOf(false) }
    var resting by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(world?.restUntil,status.paused,status.working) {
        if(resting && world?.restUntil==null && !status.working && !status.paused) {
            resting=false;finished=status.generated
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
        if(universe.clockWarning.isNotBlank()) Text(universe.clockWarning,fontSize=12.sp,color=colors.secondaryText)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(world?.let { "${LifeRules.weather(it,it.map.userLocation)} · 世界时间 3:1" } ?: "尚未建立世界",
                Modifier.weight(1f),fontSize=12.sp,color=colors.secondaryText)
            TextButton(onClick={choose=true},enabled=world!=null && world.restUntil==null) { Text("休息") }
        }
        status.error?.let { error ->
            Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
            Row {
                TextButton(onClick=runtime::resume){Text("继续处理")}
                if(status.working) TextButton(onClick=runtime::stop){Text("暂停")}
            }
        }
        if(world!=null && world.target-world.now>5*GAME_MINUTE && !resting) {
            Text("正在补齐至 ${gameLabel(world.target)}，已到 ${gameLabel(world.now)}",fontSize=12.sp,color=colors.secondaryText)
            if(!status.paused) TextButton(onClick=runtime::stop){Text("稍后继续")}
        }
    }
    if(choose) AlertDialog(onDismissRequest={choose=false},title={Text("休息多久？")},
        text={Column { listOf(30 to "30 分钟",120 to "2 小时",480 to "8 小时",1440 to "24 小时").forEach { (minutes,label)->
            TextButton(onClick={ choose=false;resting=true;runtime.rest(minutes) },modifier=Modifier.fillMaxWidth()){Text(label)}
        } }},confirmButton={},dismissButton={TextButton(onClick={choose=false}){Text("取消")}})
    if(resting) {
        var animationTime by remember { mutableLongStateOf(world?.now ?: 0L) }
        LaunchedEffect(Unit) { while(true) { delay(80);animationTime+=5*GAME_MINUTE } }
        AlertDialog(onDismissRequest={runtime.stop();resting=false},title={Text("休息中")},
            text={Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
                Text(gameLabel(animationTime,false),fontSize=42.sp)
                Text(if(status.paused) status.error.orEmpty() else "正在整理这段时间的生活和留言…")
                Text(world?.let { "已处理到 ${gameLabel(it.now)}" }.orEmpty(),fontSize=12.sp)
            }},confirmButton={if(status.paused) TextButton(onClick=runtime::resume){Text("重试 / 继续")}},
            dismissButton={TextButton(onClick={runtime.stop();resting=false}){Text("稍后继续")}})
    }
    finished?.let { count ->
        AlertDialog(onDismissRequest={finished=null},title={Text("休息结束")},
            text={Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("这段时间收到了 $count 条消息，已放入各自的聊天。")
                status.notices.forEach { notice -> Text(notice,Modifier.padding(vertical=8.dp));HorizontalDivider() }
            }},confirmButton={TextButton(onClick={finished=null}){Text("查看聊天")}})
    }
}
