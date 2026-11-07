package me.rerere.momoly.ui.world

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import me.rerere.momoly.data.world.*
import me.rerere.momoly.data.settings.ThemeMode
import me.rerere.momoly.ui.theme.MomolyTheme
import me.rerere.momoly.ui.theme.ProvideWeChatColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class WorldMapPageTest {
    @get:Rule(order = 0) val timeout = TimeoutRule.seconds(90)
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun world(name: String = "测试世界"): LifeWorld {
        val initial = LifeInitial(initialTime = "2026-11-06T12:00:00", initialLocation = "home",
            background = "背景", history = "不应出现在地图的资料")
        val actor = LifeActor("actor.json", "角色甲", initial, "home", "背景", emptyList(), 50,
            "平静", 50, nextContact = Long.MAX_VALUE)
        return LifeWorld(name, WorldMap(places = listOf(WorldPlace("home", "住处", x = 0f, y = .3f),
            WorldPlace("park", "公园", x = 1f, y = .6f)), routes = listOf(WorldRoute("home", "park", 25)),
            userLocation = "home"), now = 0L, target = 0L, actors = mapOf(actor.file to actor))
    }

    @Test fun namesAreAbovePlacesAndTravelTimeRequiresTappingTheEdge() {
        compose.setContent { MomolyTheme { ProvideWeChatColors { WorldMapPage(world(), "小明") } } }
        compose.onNodeWithTag("route_duration").assertDoesNotExist()
        compose.onNodeWithText("不应出现在地图的资料").assertDoesNotExist()
        compose.onNodeWithTag("map_people_home").assertTextEquals("小明\n角色甲")
        val names = compose.onNodeWithTag("map_people_home").fetchSemanticsNode().boundsInRoot
        val place = compose.onNodeWithTag("map_place_home").fetchSemanticsNode().boundsInRoot
        assertTrue(names.bottom < place.top)
        assertTrue(kotlin.math.abs(names.center.x - place.center.x) < 1f)
        compose.onNodeWithTag("map_route_0").performTouchInput { click(center) }
        compose.onNodeWithTag("route_duration").assertExists()
        compose.onNodeWithTag("route_close").performClick()
        compose.onNodeWithTag("route_duration").assertDoesNotExist()
    }

    @Test fun switchingWorldClearsRouteDetailsAndShowsNewLocation() {
        val current = mutableStateOf(world())
        compose.setContent { MomolyTheme { ProvideWeChatColors { WorldMapPage(current.value, "小明") } } }
        compose.onNodeWithTag("map_route_0").performClick()
        compose.onNodeWithTag("route_duration").assertExists()
        compose.runOnIdle {
            val next = world("另一个世界")
            current.value = next.copy(map = next.map.copy(userLocation = "park"))
        }
        compose.onNodeWithTag("route_duration").assertDoesNotExist()
        compose.onNodeWithTag("map_people_park").assertTextEquals("小明")
        compose.onNodeWithTag("map_people_home").assertTextEquals("角色甲")
    }

    @Test fun regionsKeepPlacesStableWhenPeopleSeparateAndDetailsStayBelowMap() {
        val base = world()
        val current = mutableStateOf(base.copy(map = base.map.copy(places = base.map.places.map {
            it.copy(region = if (it.id == "home") "天行市" else "临空市")
        })))
        compose.setContent { MomolyTheme { ProvideWeChatColors { WorldMapPage(current.value, "小明") } } }
        compose.onNodeWithTag("map_region_0").assertTextEquals("天行市")
        compose.onNodeWithTag("map_region_1").assertTextEquals("临空市")
        val before = compose.onNodeWithTag("map_place_home").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            current.value = current.value.copy(actors = current.value.actors.mapValues { (_, actor) -> actor.copy(location = "park") })
        }
        val after = compose.onNodeWithTag("map_place_home").fetchSemanticsNode().boundsInRoot
        assertTrue("地点在人物移动后偏移：$before -> $after", kotlin.math.abs(before.top - after.top) < 1f)
        compose.onNodeWithTag("map_route_0").performClick()
        compose.onNodeWithTag("route_duration").assertIsDisplayed()
        compose.onNodeWithTag("world_topology").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test fun crowdedRegionWrapsIntoRowsAtNarrowWidthAndLargeFont() {
        val base = world()
        val crowded = base.copy(map = base.map.copy(places = (0..5).map { index ->
            WorldPlace(if (index == 0) "home" else "place$index", "很长的地点名称$index", region = "同一个地区",
                x = index / 100f, y = .5f)
        }, routes = emptyList()))
        compose.setContent { MomolyTheme { ProvideWeChatColors {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
                Box(Modifier.width(280.dp)) { WorldMapPage(crowded, "很长的用户名称") }
            }
        } } }
        val map = compose.onNodeWithTag("world_topology").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("map_place_home").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("map_place_place1").fetchSemanticsNode().boundsInRoot
        val names = compose.onNodeWithTag("map_people_home").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left >= map.left && second.right <= map.right)
        assertTrue(first.right <= second.left)
        assertTrue(names.bottom < first.top)
        compose.onNodeWithTag("map_place_place5").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun sevenPlaceMapKeepsLabelsSeparateInBothThemes() {
        val base = world()
        val sample = base.copy(map = WorldMap(places = listOf(
            WorldPlace("home", "临空市住处", "临空市", .12f, .8f),
            WorldPlace("association", "猎人协会", "临空市", .4f, .82f),
            WorldPlace("quarters", "天行市住处", "天行市", .16f, .44f),
            WorldPlace("supply", "舰队补给点", "天行市", .53f, .46f),
            WorldPlace("command", "航天署指挥区", "天行市", .73f, .18f),
            WorldPlace("hangar", "飞行训练场", "天行市", .23f, .14f),
            WorldPlace("patrol", "近空巡逻区", "天行市", .88f, .52f),
        ), routes = listOf(WorldRoute("home", "association", 25), WorldRoute("home", "quarters", 45),
            WorldRoute("quarters", "supply", 15), WorldRoute("quarters", "hangar", 12),
            WorldRoute("quarters", "command", 20), WorldRoute("supply", "command", 15), WorldRoute("command", "patrol", 20)),
            userLocation = "home"), actors = base.actors.mapValues { (_, actor) -> actor.copy(location = "quarters") })
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { MomolyTheme(themeMode = theme.value) { ProvideWeChatColors { WorldMapPage(sample, "小羊君") } } }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            compose.runOnIdle { theme.value = mode }
            val labels = sample.map.places.map {
                compose.onNodeWithTag("map_place_${it.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            labels.forEachIndexed { index, label ->
                labels.drop(index + 1).forEach { other -> assertTrue("地点文字重叠", !label.overlaps(other)) }
            }
        }
    }
}
