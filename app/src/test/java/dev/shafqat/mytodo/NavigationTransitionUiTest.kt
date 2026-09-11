package dev.shafqat.mytodo

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shafqat.mytodo.ui.navigation.Screen
import dev.shafqat.mytodo.ui.navigation.ScreenTransitionMillis
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The regression: while a screen faded out it still accepted touches, so tapping a list right after
 * backing out of one ticked whatever item sat under the finger instead of opening the list.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class NavigationTransitionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var tapsOnDetail = 0

    @Composable
    private fun TwoScreenApp() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = { fadeIn(tween(ScreenTransitionMillis)) },
            exitTransition = { fadeOut(tween(ScreenTransitionMillis)) },
            popEnterTransition = { fadeIn(tween(ScreenTransitionMillis)) },
            popExitTransition = { fadeOut(tween(ScreenTransitionMillis)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") { entry ->
                Screen(entry) {
                    TextButton(onClick = { navController.navigate("detail") }) { Text("Open") }
                }
            }
            composable("detail") { entry ->
                Screen(entry) {
                    Column {
                        TextButton(onClick = { tapsOnDetail++ }) { Text("Tick") }
                        TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                    }
                }
            }
        }
    }

    private fun openDetail() {
        composeRule.setContent { TwoScreenApp() }
        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap that lands while a screen is fading out is ignored`() {
        openDetail()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Back").performClick()
        // Partway through the fade, the outgoing screen is still composed and on screen.
        composeRule.mainClock.advanceTimeBy(ScreenTransitionMillis / 3L)
        composeRule.onNodeWithText("Tick").performClick()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals("the outgoing screen must not accept taps", 0, tapsOnDetail)
    }

    @Test
    fun `a tap on a settled screen still works`() {
        openDetail()

        composeRule.onNodeWithText("Tick").performClick()
        composeRule.waitForIdle()

        assertEquals(1, tapsOnDetail)
    }

    @Test
    fun `navigation still completes after a blocked tap`() {
        openDetail()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Back").performClick()
        composeRule.mainClock.advanceTimeBy(ScreenTransitionMillis / 3L)
        composeRule.onNodeWithText("Tick").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // Back out fully, and the home screen is usable again.
        composeRule.onNodeWithText("Open").assertExists()
        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tick").assertExists()
    }
}
