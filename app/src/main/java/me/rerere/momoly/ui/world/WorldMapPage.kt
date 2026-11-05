package me.rerere.momoly.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.momoly.R
import me.rerere.momoly.data.world.*
import me.rerere.momoly.ui.components.appClickable
import me.rerere.momoly.ui.theme.weChatColors
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun WorldMapPage(world: LifeWorld?, userName: String = "") {
    val colors = weChatColors()
    var selectedRoute by remember(world?.name, world?.map) { mutableStateOf<WorldRoute?>(null) }
    CompositionLocalProvider(LocalContentColor provides colors.incomingText) {
        if (world == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.world_map_empty), Modifier.padding(24.dp))
            }
            return@CompositionLocalProvider
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("world_map"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(world.name, style = MaterialTheme.typography.titleLarge)
            Text(gameLabel(world.target), style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
            if (world.target - world.now > GAME_MINUTE) {
                Text(stringResource(R.string.world_map_updating, gameLabel(world.now)),
                    style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
            }
            Text(stringResource(R.string.world_map_weather, world.map.name(world.map.userLocation),
                LifeRules.weather(world, world.map.userLocation)), style = MaterialTheme.typography.bodyMedium)
            WorldTopology(world, userName.ifBlank { stringResource(R.string.world_map_you) }, selectedRoute) {
                selectedRoute = it
            }
            Text(stringResource(R.string.world_map_hint), style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText)
        }
        selectedRoute?.let { route ->
            AlertDialog(
                onDismissRequest = { selectedRoute = null },
                title = { Text("${world.map.name(route.from)} ↔ ${world.map.name(route.to)}") },
                text = { Text(stringResource(R.string.world_map_duration, route.minutes), Modifier.testTag("route_duration")) },
                confirmButton = { TextButton(onClick = { selectedRoute = null }, modifier = Modifier.testTag("route_close")) { Text(stringResource(R.string.world_map_close)) } },
            )
        }
    }
}

@Composable
private fun WorldTopology(world: LifeWorld, userName: String, selectedRoute: WorldRoute?, onRoute: (WorldRoute) -> Unit) {
    val colors = weChatColors()
    val people = world.map.places.associate { place ->
        place.id to buildList {
            if (place.id == world.map.userLocation) add(userName)
            world.actors.values.filter { it.location == place.id }.forEach { actor ->
                add(if (actor.travel == null) actor.name else stringResource(R.string.world_map_travelling, actor.name))
            }
        }.joinToString("\n")
    }
    // 给边界地点和换行姓名留出空间；窄屏平移地图，不压缩文字。
    val topInset = (72 + (people.values.maxOfOrNull { it.count { c -> c == '\n' } } ?: 0) * 20).dp
    val minX = world.map.places.minOf { it.x }
    val maxX = world.map.places.maxOf { it.x }
    val minY = world.map.places.minOf { it.y }
    val maxY = world.map.places.maxOf { it.y }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.listBackground)) {
        val mapWidth = maxWidth.coerceAtLeast(320.dp)
        val mapHeight = 320.dp + topInset
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.requiredSize(mapWidth, mapHeight).testTag("world_topology")) {
                fun x(place: WorldPlace) = 64.dp + (mapWidth - 128.dp) *
                    (if (maxX > minX) (place.x - minX) / (maxX - minX) else .5f)
                fun y(place: WorldPlace) = topInset + (mapHeight - topInset - 64.dp) *
                    (if (maxY > minY) (place.y - minY) / (maxY - minY) else .5f)
                val places = world.map.places.associateBy { it.id }
                Canvas(Modifier.fillMaxSize()) {
                    fun point(place: WorldPlace) = Offset(x(place).toPx(), y(place).toPx())
                    world.map.routes.forEach { route ->
                        val from = places[route.from] ?: return@forEach
                        val to = places[route.to] ?: return@forEach
                        drawLine(if (route == selectedRoute) colors.sendButton else colors.secondaryText.copy(alpha = .35f),
                            point(from), point(to), (if (route == selectedRoute) 4.dp else 2.dp).toPx(), StrokeCap.Round)
                    }
                    world.map.places.forEach { place ->
                        val occupied = !people[place.id].isNullOrBlank()
                        if (occupied) drawCircle(colors.sendButton.copy(alpha = .14f), 18.dp.toPx(), point(place))
                        drawCircle(colors.listBackground, 10.dp.toPx(), point(place))
                        drawCircle(if (occupied) colors.sendButton else colors.secondaryText, (if (occupied) 8.dp else 5.dp).toPx(), point(place))
                    }
                }
                world.map.routes.forEachIndexed { index, route ->
                    val from = places[route.from] ?: return@forEachIndexed
                    val to = places[route.to] ?: return@forEachIndexed
                    val dx = (x(to) - x(from)).value
                    val dy = (y(to) - y(from)).value
                    val length = sqrt(dx * dx + dy * dy).dp
                    val description = stringResource(R.string.world_map_route, from.name, to.name)
                    // 点击带沿着连线旋转，保留标准点击、键盘与无障碍操作。
                    Box(Modifier.offset((x(from) + x(to)) / 2 - length / 2, (y(from) + y(to)) / 2 - 20.dp)
                        .requiredSize(length, 40.dp)
                        .graphicsLayer { rotationZ = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() }
                        .testTag("map_route_$index").semantics { contentDescription = description }
                        .appClickable(onClick = { onRoute(route) }))
                }
                Layout(content = {
                    world.map.places.forEach { place ->
                        val names = people.getValue(place.id)
                        Text(names, Modifier.testTag("map_people_${place.id}")
                            .background(colors.listBackground.copy(alpha = .95f), RoundedCornerShape(8.dp)).padding(4.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            color = colors.incomingText)
                        Text(place.name, Modifier.testTag("map_place_${place.id}")
                            .background(if (names.isNotBlank()) colors.sendButton.copy(alpha = .14f) else colors.listBackground,
                                RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
                            fontSize = 12.sp, fontWeight = if (names.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center, color = colors.incomingText)
                    }
                }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                    val labels = measurables.map { it.measure(Constraints(maxWidth = 120.dp.roundToPx())) }
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        world.map.places.forEachIndexed { index, place ->
                            val cx = x(place).toPx().roundToInt()
                            val cy = y(place).toPx().roundToInt()
                            val names = labels[index * 2]
                            val name = labels[index * 2 + 1]
                            names.place(cx - names.width / 2, cy - 20.dp.roundToPx() - names.height)
                            name.place(cx - name.width / 2, cy + 20.dp.roundToPx())
                        }
                    }
                }
            }
        }
    }
}
