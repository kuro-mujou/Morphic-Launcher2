package inkspire.morphic.core.designsystem.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import inkspire.morphic.core.model.DeviceConfiguration

/**
 * Maps a [WindowSizeClass] to a [DeviceConfiguration]. Width decides phone vs tablet; a short height at any
 * width is treated as a phone in landscape.
 */
fun DeviceConfiguration.Companion.fromWindowSizeClass(
    windowSizeClass: WindowSizeClass,
): DeviceConfiguration {
    val width = windowSizeClass.minWidthDp
    val height = windowSizeClass.minHeightDp
    return when {
        width >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND ->
            if (height < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) DeviceConfiguration.PHONE_LANDSCAPE
            else DeviceConfiguration.TABLET_LANDSCAPE

        width >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND ->
            if (height < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) DeviceConfiguration.PHONE_LANDSCAPE
            else DeviceConfiguration.TABLET_PORTRAIT

        else -> DeviceConfiguration.PHONE_PORTRAIT
    }
}

/** The current [DeviceConfiguration], derived from the active window's adaptive info. */
@Composable
@Suppress("DEPRECATION")
fun currentDeviceConfiguration(): DeviceConfiguration =
    DeviceConfiguration.fromWindowSizeClass(currentWindowAdaptiveInfo().windowSizeClass)
