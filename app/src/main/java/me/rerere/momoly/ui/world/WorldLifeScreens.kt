package me.rerere.momoly.ui.world

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.momoly.data.world.*
import me.rerere.momoly.ui.theme.weChatColors

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
                if(world.target-world.now>GAME_MINUTE) Text("正在补齐至 ${gameLabel(world.target)}；以下为已处理时刻的状态。")
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
    val colors=weChatColors()
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
        status.error?.let { error ->
            Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
            Row {
                TextButton(onClick=runtime::resume){Text("继续处理")}
                if(status.working) TextButton(onClick=runtime::stop){Text("暂停")}
            }
        }
        if(world!=null && world.target-world.now>5*GAME_MINUTE) {
            Text("正在补齐至 ${gameLabel(world.target)}，已到 ${gameLabel(world.now)}",fontSize=12.sp,color=colors.secondaryText)
            if(!status.paused) TextButton(onClick=runtime::stop){Text("稍后继续")}
        }
    }
}
