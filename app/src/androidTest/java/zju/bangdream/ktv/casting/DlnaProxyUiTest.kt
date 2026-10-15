package zju.bangdream.ktv.casting

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import zju.bangdream.ktv.casting.ui.components.DlnaEnvironmentDialog
import zju.bangdream.ktv.casting.ui.components.DlnaProxyControls
import zju.bangdream.ktv.casting.ui.screens.CastingControlContent
import zju.bangdream.ktv.casting.ui.screens.DeviceSelectorScreen
import zju.bangdream.ktv.casting.ui.screens.SettingsScreen

@RunWith(AndroidJUnit4::class)
class DlnaProxyUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("ktv_settings", Context.MODE_PRIVATE)

    @Before fun reset() {
        prefs.edit().clear().commit()
        RustEngine.setDlnaDirect(false)
    }

    @Test fun legacyDirectChoiceAndBothSwitchesStayInSync() {
        prefs.edit().putBoolean("dlna_direct", true).commit()
        compose.setContent { MaterialTheme { Column { DlnaProxyControls(); DlnaProxyControls() } } }
        compose.onAllNodes(isToggleable())[0].assertIsOff().performClick()
        compose.onAllNodes(isToggleable())[1].assertIsOn()
        compose.runOnIdle {
            assertFalse(prefs.getBoolean("dlna_direct", true))
            assertFalse(RustEngine.getDlnaDirect())
        }
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOff()
        compose.runOnIdle {
            assertTrue(prefs.getBoolean("dlna_direct", false))
            assertTrue(RustEngine.getDlnaDirect())
        }
    }

    @Test fun pureKChoiceEnablesProxyBeforeContinuing() {
        verifyEnvironmentChoice("是，在纯K", true)
    }

    @Test fun otherEnvironmentChoiceDisablesProxyBeforeContinuing() {
        verifyEnvironmentChoice("否，其他环境", false)
    }

    private fun verifyEnvironmentChoice(label: String, expected: Boolean) {
        var continued = false
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) DlnaEnvironmentDialog(
                    onChoice = {
                        DlnaProxySettings.setEnabled(context, it)
                        assertEquals(expected, DlnaProxySettings.isEnabled(context))
                        assertEquals(!expected, RustEngine.getDlnaDirect())
                        continued = true
                        visible = false
                    },
                    onDismiss = { visible = false }
                )
            }
        }
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertTrue(continued) }
        compose.onNodeWithText("是否在纯K？").assertDoesNotExist()
    }

    @Test fun settingsShowsShortProxyLabel() {
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}, onOpenLogs = {}) } }
        compose.onNodeWithText("反向代理").assertIsDisplayed()
        compose.onNodeWithText("DLNA 原始链接直连").assertDoesNotExist()
        compose.onNode(isToggleable()).assertIsOn()
        screenshot("settings-proxy")
    }

    @Test fun clickingDlnaAsksAboutEnvironment() {
        compose.setContent {
            MaterialTheme {
                DeviceSelectorScreen(
                    activeRoom = ActiveRoom("https://example.test", "test-room"),
                    onActiveRoomChange = {}, onDeviceSelect = { _, _, _ -> },
                    onBilibiliMode = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText("搜索 DLNA 设备").performScrollTo().performClick()
        compose.onNodeWithText("是否在纯K？").assertIsDisplayed()
        screenshot("dlna-environment")
        // Dismissing the question must leave the user's stored choice untouched.
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertFalse(prefs.contains("dlna_direct")) }
    }

    @Test fun playbackShowsProxySwitchAndDirectModeHasNo1080Option() {
        DlnaProxySettings.setEnabled(context, false)
        compose.setContent {
            MaterialTheme {
                CastingControlContent(
                    deviceName = "DLNA 测试设备", roomId = "test-room", songTitle = "测试歌曲",
                    currentSec = 30, totalSec = 240, isPlaying = true,
                    onTogglePause = {}, onNext = {}, onPrev = {}, onSeek = {}, onReset = {}, onRecast = {}
                )
            }
        }
        compose.onNodeWithText("反向代理").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff()
        screenshot("playback-proxy")
        compose.onNodeWithText("720P").performScrollTo().performClick()
        compose.onNodeWithText("1080P (Beta)").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot()
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        // Gradle uninstalls the test app afterwards, so preserve images outside its data directory.
        val command = "mkdir -p /sdcard/Download/ktv-ui-screenshots && " +
            "cp '${file.absolutePath}' '/sdcard/Download/ktv-ui-screenshots/$name.png' && echo saved"
        val output = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        assertTrue("Screenshot export failed: $output", output.contains("saved"))
    }
}
