package me.rerere.momoly.ui.world

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import me.rerere.momoly.data.world.*
import me.rerere.momoly.ui.theme.MomolyTheme
import me.rerere.momoly.ui.theme.ProvideWeChatColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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
}
