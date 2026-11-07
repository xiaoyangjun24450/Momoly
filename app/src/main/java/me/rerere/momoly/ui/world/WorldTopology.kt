package me.rerere.momoly.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
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

private data class MapRegion(val name: String, val rows: List<List<WorldPlace>>) {
    val places = rows.flatten()
}

private fun mapRegions(map: WorldMap): List<MapRegion> = map.places.groupBy { it.region.trim() }.entries
    .sortedWith(compareBy<Map.Entry<String, List<WorldPlace>>> { it.value.map(WorldPlace::y).average() }.thenBy { it.key })
    .map { (name, places) ->
        val rows = mutableListOf<MutableList<WorldPlace>>()
        places.sortedWith(compareBy<WorldPlace> { it.y }.thenBy { it.x }.thenBy { it.id }).forEach { place ->
            if (rows.isEmpty() || place.y - rows.last().first().y > .14f) rows.add(mutableListOf())
            rows.last().add(place)
        }
        MapRegion(name, rows.map { row -> row.sortedWith(compareBy<WorldPlace> { it.x }.thenBy { it.id }) })
    }

private data class RegionDrawing(val regions: List<Rect> = emptyList(), val points: Map<String, Offset> = emptyMap())

@Composable
internal fun WorldTopology(world: LifeWorld, userName: String, selectedRoute: WorldRoute?, onRoute: (WorldRoute) -> Unit) {
    val colors = weChatColors()
    val regions = remember(world.map) { mapRegions(world.map) }
    val places = regions.flatMap { it.places }
    val placeIndices = places.mapIndexed { index, place -> place.id to index }.toMap()
    val people = places.associate { place ->
        place.id to buildList {
            if (place.id == world.map.userLocation) add(userName)
            world.actors.values.filter { it.location == place.id }.forEach { actor ->
                add(if (actor.travel == null) actor.name else stringResource(R.string.world_map_travelling, actor.name))
            }
        }.joinToString("\n")
    }
    val surfaces = regions.indices.map { index ->
        (if (index % 2 == 0) colors.sendButton else Color(0xFFB6A77D)).copy(alpha = .065f)
            .compositeOver(colors.chatBackground)
    }
    val nameSurface = colors.sendButton.copy(alpha = .13f).compositeOver(colors.listBackground)
    var drawing by remember { mutableStateOf(RegionDrawing()) }
    val reservedNames = remember(world.name, world.map.places) { intArrayOf(0) }
    Layout(content = {
        regions.forEachIndexed { index, region ->
            Text(region.name, Modifier.testTag("map_region_$index"), fontSize = 12.sp, lineHeight = 16.sp,
                color = colors.incomingText.copy(alpha = .58f))
        }
        regions.forEachIndexed { index, region ->
            region.places.forEach { place ->
                val names = people.getValue(place.id)
                Text(names, Modifier.testTag("map_people_${place.id}")
                    .background(if (names.isBlank()) Color.Transparent else nameSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    color = colors.incomingText)
                Text(place.name, Modifier.testTag("map_place_${place.id}").background(surfaces[index])
                    .padding(horizontal = 3.dp, vertical = 2.dp), fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = if (names.isBlank()) FontWeight.Normal else FontWeight.Medium,
                    textAlign = TextAlign.Center, color = colors.incomingText)
            }
        }
        world.map.routes.forEachIndexed { index, route ->
            val description = stringResource(R.string.world_map_route, world.map.name(route.from), world.map.name(route.to))
            Box(Modifier.testTag("map_route_$index").semantics { contentDescription = description }
                .appClickable(onClick = { onRoute(route) }))
        }
    }, modifier = Modifier.fillMaxWidth().testTag("world_topology").drawBehind {
        drawing.regions.forEachIndexed { index, bounds ->
            drawRoundRect(surfaces[index], bounds.topLeft, bounds.size, CornerRadius(24.dp.toPx()))
        }
        world.map.routes.forEach { route ->
            val from = drawing.points[route.from] ?: return@forEach
            val to = drawing.points[route.to] ?: return@forEach
            val selected = route == selectedRoute
            drawLine(if (selected) colors.sendButton else colors.incomingText.copy(alpha = .21f),
                from, to, (if (selected) 2.5.dp else 1.25.dp).toPx(), StrokeCap.Round)
        }
        places.forEach { place ->
            val point = drawing.points[place.id] ?: return@forEach
            val occupied = !people[place.id].isNullOrBlank()
            drawCircle(colors.listBackground, (if (occupied) 8.dp else 5.dp).toPx(), point)
            if (occupied) {
                drawCircle(colors.sendButton, 5.5.dp.toPx(), point)
                drawLine(colors.sendButton.copy(alpha = .5f), point - Offset(0f, 8.dp.toPx()),
                    point - Offset(0f, 12.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
            } else drawCircle(colors.incomingText.copy(alpha = .28f), 4.dp.toPx(), point, style = Stroke(1.2.dp.toPx()))
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val inset = 12.dp.roundToPx()
        val columns = ((width - inset * 2) / 88.dp.roundToPx()).coerceIn(1, 4)
        val fittedRegions = regions.map { it.copy(rows = it.rows.flatMap { row -> row.chunked(columns) }) }
        val maxColumns = fittedRegions.maxOfOrNull { it.rows.maxOf { row -> row.size } } ?: 1
        val labelWidth = minOf(128.dp.roundToPx(), (width - inset * 2) / maxColumns - 8.dp.roundToPx()).coerceAtLeast(1)
        val titles = measurables.take(regions.size).map { it.measure(Constraints(maxWidth = (width - inset * 2).coerceAtLeast(0))) }
        val labels = measurables.drop(regions.size).take(places.size * 2).map { it.measure(Constraints(maxWidth = labelWidth)) }
        // 同一张图已用过的姓名空间不回缩，人员分开后地点不会随之跳动。
        reservedNames[0] = maxOf(reservedNames[0], (32.dp.toPx() * fontScale).roundToInt(),
            labels.filterIndexed { index, _ -> index % 2 == 0 }.maxOfOrNull { it.height } ?: 0)
        val nameHeight = reservedNames[0]
        val placeHeight = labels.filterIndexed { index, _ -> index % 2 == 1 }.maxOfOrNull { it.height } ?: 0
        val rowHeight = nameHeight + placeHeight + 30.dp.roundToPx()
        val points = mutableMapOf<String, Offset>()
        val bounds = mutableListOf<Rect>()
        val titlePositions = mutableListOf<Int>()
        var top = 0
        fittedRegions.forEachIndexed { index, region ->
            val header = if (region.name.isBlank()) 0 else titles[index].height + 8.dp.roundToPx()
            titlePositions.add(top + inset)
            val regionHeight = inset + header + region.rows.size * rowHeight
            bounds.add(Rect(0f, top.toFloat(), width.toFloat(), (top + regionHeight).toFloat()))
            val minX = region.places.minOf { it.x }
            val maxX = region.places.maxOf { it.x }
            val left = inset + labelWidth / 2f
            val right = width - left
            region.rows.forEachIndexed { rowIndex, row ->
                val ideal = row.map { left + (right - left) * if (maxX > minX) (it.x - minX) / (maxX - minX) else .5f }
                val spread = ideal.zipWithNext().any { (a, b) -> b - a < labelWidth + 8.dp.toPx() }
                row.forEachIndexed { column, place ->
                    val x = if (spread) left + (right - left) * column / (row.size - 1) else ideal[column]
                    val y = top + inset + header + rowIndex * rowHeight + nameHeight + 12.dp.roundToPx()
                    points[place.id] = Offset(x, y.toFloat())
                }
            }
            top += regionHeight + 12.dp.roundToPx()
        }
        drawing = RegionDrawing(bounds, points)
        val routes = measurables.drop(regions.size + places.size * 2).mapIndexed { index, measurable ->
            val route = world.map.routes[index]
            val from = points.getValue(route.from)
            val to = points.getValue(route.to)
            measurable.measure(Constraints.fixed((to - from).getDistance().roundToInt().coerceAtLeast(1), 40.dp.roundToPx()))
        }
        layout(width, (top - 12.dp.roundToPx()).coerceAtLeast(0)) {
            titles.forEachIndexed { index, title -> title.place(inset, titlePositions[index]) }
            routes.forEachIndexed { index, route ->
                val data = world.map.routes[index]
                val from = points.getValue(data.from)
                val to = points.getValue(data.to)
                val center = (from + to) / 2f
                route.placeWithLayer((center.x - route.width / 2f).roundToInt(), (center.y - route.height / 2f).roundToInt(), zIndex = -1f) {
                    rotationZ = Math.toDegrees(atan2(to.y - from.y, to.x - from.x).toDouble()).toFloat()
                }
            }
            places.forEach { place ->
                val index = placeIndices.getValue(place.id)
                val point = points.getValue(place.id)
                val names = labels[index * 2]
                val name = labels[index * 2 + 1]
                names.place((point.x - names.width / 2f).roundToInt(), point.y.roundToInt() - 12.dp.roundToPx() - names.height)
                name.place((point.x - name.width / 2f).roundToInt(), point.y.roundToInt() + 10.dp.roundToPx())
            }
        }
    }
}
