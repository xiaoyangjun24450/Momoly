package me.rerere.momoly.ui.chat

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import me.rerere.momoly.data.settings.WorldUserProfileStore
import me.rerere.momoly.data.world.PlaceholderWorldContextProvider
import me.rerere.momoly.ui.theme.MomolyTheme
import me.rerere.momoly.ui.theme.ProvideWeChatColors
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalTestApi::class)
class WorldProfileEditorTest {
    @get:Rule(order = 0) val timeout = TimeoutRule.seconds(90)
    @get:Rule(order = 1) val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val controller = ChatUserProfileController(AndroidChatUserProfileDataSource(context))

    @Before fun clean() {
        context.getSharedPreferences("world_user_profiles", 0).edit().clear().commit()
    }

    @Test fun homeAvatarIsTheProfileEntryAndKeepsThreeExistingTabs() {
        var opened = false
        compose.setContent {
            MomolyTheme { ProvideWeChatColors {
                HomePage(emptyList(), "Campus", PlaceholderWorldContextProvider.snapshot("Campus", null),
                    HomeTab.Contacts, onTab = {}, onAddContact = {}, onSelectWorld = {}, onOpenCharacter = {},
                    profile = ChatUserProfile("Alice", "Student", 0, null), onEditProfile = { opened = true })
            } }
        }
        compose.onNodeWithTag("home_user_avatar").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
        listOf("Contacts", "Worlds", "Settings").forEach {
            compose.onNodeWithTag("home_tab_$it").assertIsDisplayed()
        }
    }

    @Test fun editsStayInDraftUntilSavedAndOnlyChangeTheirWorld() {
        WorldUserProfileStore.save(context, "Campus", "Alice", "Student", null, false)
        WorldUserProfileStore.save(context, "Fantasy", "Elf", "Mage", null, false)
        var saved = false
        compose.setContent { MomolyTheme { ProvideWeChatColors {
            WorldProfileEditor("Campus", controller.load("Campus"), controller, {}, { saved = true })
        } } }
        compose.onNodeWithTag("world_profile_name").performTextReplacement("New Alice")
        compose.onNodeWithTag("world_profile_description").performTextReplacement("Researcher")
        assertEquals("Alice", controller.load("Campus").name)
        compose.onNodeWithTag("world_profile_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { saved }
        assertEquals("New Alice", controller.load("Campus").name)
        assertEquals("Researcher", controller.load("Campus").description)
        assertEquals("Elf", controller.load("Fantasy").name)
        assertEquals("Mage", controller.load("Fantasy").description)
    }
}
