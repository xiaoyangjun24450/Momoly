package me.rerere.momoly.ui.chat

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import me.rerere.momoly.data.world.PlaceholderWorldContextProvider
import me.rerere.momoly.ui.theme.ProvideWeChatColors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.junit.rules.TimeoutRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule(order = 0) val timeout = TimeoutRule.seconds(90)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun launchStartsAtHome() {
        compose.setContent {
            val stack = rememberChatNavigationStack(false)
            assertEquals(listOf(ChatDestination.Home), stack.toList())
        }
        compose.waitForIdle()
    }

    @Test
    fun contactsAreFilteredByWorldAndWorldTabSwitchesTheList() {
        val selected = mutableStateOf("Campus")
        val tab = mutableStateOf(HomeTab.Contacts)
        var added = 0
        var opened = ""
        compose.setContent {
            ProvideWeChatColors {
                HomePage(
                    contacts = listOf(contact("Alice", "Campus"), contact("Elf", "Fantasy")),
                    selectedWorld = selected.value,
                    worldContext = PlaceholderWorldContextProvider.snapshot(selected.value, null),
                    tab = tab.value,
                    onTab = { tab.value = it },
                    onAddContact = { added++ },
                    onSelectWorld = { selected.value = it; tab.value = HomeTab.Contacts },
                    onOpenCharacter = { opened = it.fileName },
                )
            }
        }
        compose.onNodeWithTag("home_contact_Alice").assertIsDisplayed().performClick()
        compose.onNodeWithTag("home_contact_Elf").assertDoesNotExist()
        compose.runOnIdle { assertEquals("Alice", opened) }
        compose.onNodeWithTag("home_add_contact").performClick()
        compose.runOnIdle { assertEquals(1, added) }
        compose.onNodeWithTag("home_tab_Worlds").performClick()
        compose.onNodeWithText("Fantasy").performClick()
        compose.onNodeWithTag("home_contact_Elf").assertIsDisplayed()
        compose.onNodeWithTag("home_contact_Alice").assertDoesNotExist()
        compose.onNodeWithTag("home_tab_Settings").assertIsDisplayed()
    }

    @Test
    fun worldChoiceAndConfirmationHandleFirstImportAndUnassignedCards() {
        assertEquals("Campus", resolveHomeWorld(null, listOf("", "Campus", "Fantasy")))
        assertEquals("Fantasy", resolveHomeWorld("Fantasy", listOf("Campus", "Fantasy")))
        assertEquals("", resolveHomeWorld("", listOf("", "Campus")))
        assertFalse(needsWorldConfirmation(null, "Campus"))
        assertFalse(needsWorldConfirmation("Campus", "Campus"))
        assertTrue(needsWorldConfirmation("Campus", "Fantasy"))
        val snapshot = PlaceholderWorldContextProvider.snapshot("Campus", "Alice")
        assertEquals("Campus", snapshot.worldName)
        assertEquals("Alice", snapshot.characterId)
        assertTrue(snapshot.isPlaceholder)
    }

    @Test fun previewUsesCurrentWorldIdentityWithoutRewritingTheMessage() {
        val name=mutableStateOf("小羊君")
        val entry=contact("Alice","Campus").copy(lastMessage="{{char}}向{{user}}问好")
        compose.setContent {
            ProvideWeChatColors {
                HomePage(contacts=listOf(entry),selectedWorld="Campus",
                    worldContext=PlaceholderWorldContextProvider.snapshot("Campus",null),
                    tab=HomeTab.Contacts,onTab={},onAddContact={},onSelectWorld={},onOpenCharacter={},
                    profile=ChatUserProfile(name.value,"",0L,null))
            }
        }
        compose.onNodeWithText("Alice向小羊君问好").assertIsDisplayed()
        compose.runOnIdle { name.value="另一个名字" }
        compose.onNodeWithText("Alice向另一个名字问好").assertIsDisplayed()
        assertEquals("{{char}}向{{user}}问好",entry.lastMessage)
    }

    private fun contact(name: String, world: String) = DrawerCharacter(
        name, name, null, null, "", false, 0L, world,
    )
}
