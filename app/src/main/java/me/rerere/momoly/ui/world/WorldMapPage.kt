package me.rerere.momoly.ui.world

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.momoly.R
import me.rerere.momoly.data.world.*
import me.rerere.momoly.ui.theme.weChatColors

@Composable
internal fun WorldMapPage(world: LifeWorld?, userName: String = "") {
    val colors = weChatColors()
    var selectedRoute by remember(world?.name, world?.map) { mutableStateOf<WorldRoute?>(null) }
    BackHandler(enabled = selectedRoute != null) { selectedRoute = null }
    CompositionLocalProvider(LocalContentColor provides colors.incomingText) {
        if (world == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.world_map_empty), Modifier.padding(24.dp))
            }
            return@CompositionLocalProvider
        }
        Column(Modifier.fillMaxSize().testTag("world_map")) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(world.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(gameLabel(world.target), Modifier.weight(1f), fontSize = 12.sp,
                            color = colors.incomingText.copy(alpha = .62f))
                        val location = world.map.places.firstOrNull { it.id == world.map.userLocation }
                        Text(stringResource(R.string.world_map_weather, location?.region?.takeIf { it.isNotBlank() }
                            ?: world.map.name(world.map.userLocation), LifeRules.weather(world, world.map.userLocation)),
                            Modifier.weight(1f), fontSize = 12.sp, color = colors.incomingText.copy(alpha = .62f))
                    }
                    if (world.target - world.now > GAME_MINUTE) {
                        Text(stringResource(R.string.world_map_updating, gameLabel(world.now)),
                            fontSize = 12.sp, color = colors.incomingText.copy(alpha = .62f))
                    }
                }
                WorldTopology(world, userName.ifBlank { stringResource(R.string.world_map_you) }, selectedRoute) {
                    selectedRoute = it
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                val route = selectedRoute
                if (route == null) {
                    Text(stringResource(R.string.world_map_hint), fontSize = 12.sp,
                        color = colors.incomingText.copy(alpha = .62f))
                } else {
                    Row(Modifier.fillMaxWidth().background(colors.sendButton.copy(alpha = .06f)
                        .compositeOver(colors.listBackground), RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${world.map.name(route.from)} ↔ ${world.map.name(route.to)}", fontSize = 13.sp,
                                fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.world_map_duration, route.minutes),
                                Modifier.testTag("route_duration"), fontSize = 12.sp,
                                color = colors.incomingText.copy(alpha = .62f))
                        }
                        TextButton(onClick = { selectedRoute = null }, modifier = Modifier.testTag("route_close")) {
                            Text(stringResource(R.string.world_map_close), color = colors.incomingText)
                        }
                    }
                }
            }
        }
    }
}
