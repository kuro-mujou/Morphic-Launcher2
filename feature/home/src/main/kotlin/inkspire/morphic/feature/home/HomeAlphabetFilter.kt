package inkspire.morphic.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.backdrop.OnFilm
import inkspire.morphic.core.designsystem.cell.AppRowCell
import inkspire.morphic.core.designsystem.cell.IconMetrics
import inkspire.morphic.core.designsystem.cell.LocalIconMetrics
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.core.model.AppInfo
import inkspire.morphic.core.model.ComponentKey

/**
 * **What the A–Z rail shows while it is being scrubbed on HOME: one letter's apps, on the frost.**
 *
 * **A filter, where the APPS rail is an index, and the surface decides which.** A rail that *scrolls* needs an A–Z
 * list under it to scroll; HOME's list is the order the user put their apps in, so the only thing "go to M" can mean
 * here is *show me M*. That is the same split the category pager's letter picker is on, and it is a property of the
 * surface rather than a preference.
 *
 * **It draws the whole phone, not HOME's list.** The rail exists so a short list of chosen apps can still reach
 * everything installed without opening APPS — Niagara's arrangement, and what makes the rail worth its column. See
 * [HomeAlphabet].
 *
 * **It stays when the finger lifts.** Releasing mid-letter leaves the apps on screen to be tapped; back, or a tap on
 * the empty space around them, is what closes it. L1's strip did the other thing — put the surface back on release —
 * and `AlphabetStrip`'s own KDoc records why that makes the whole gesture a preview nobody can act on.
 *
 * **It fills the list's column and not the screen**, though the frost behind it is full-screen. Two reasons, and the
 * second is the load-bearing one: the widget area's space reading as blurred emptiness is what says HOME is still
 * there underneath, and a layer that ran the full width would put a long app name under the rail — where the column
 * ends exactly where the rail begins with nothing written down twice to keep them apart.
 *
 * **Tap-to-launch rather than the item gesture contract**, which is this file's one deliberate departure from "cells
 * carry no `onClick`". That contract exists to arbitrate a *drag* against a press, and nothing here can be dragged:
 * this is a chooser the user is passing through, not an arrangement they are editing. Adding a drag would mean
 * deciding what dropping an app from it onto HOME means, which is `AddAppsRow`'s job and already has an answer.
 *
 * @param apps the letter's run, in label order.
 * @param label the letter itself, drawn above the run — the one thing on screen that says which of them this is, the
 *   rail's own badge being under the finger that chose it.
 * @param alpha read at draw time, so the fade in runs without recomposing the rows.
 * @param rowHeight HOME's own list row height, so a filtered row is the size of the row it sits in for.
 */
@Composable
internal fun HomeAlphabetFilter(
    apps: List<AppInfo>,
    label: String,
    alpha: () -> Float,
    rowHeight: Dp,
    metrics: IconMetrics,
    onLaunch: (ComponentKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnFilm {
        val colors = LocalMorphicColors.current
        CompositionLocalProvider(LocalIconMetrics provides metrics) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha() }
                    // Silent, like every other full-screen tap-catcher here: no ripple and no click semantics. On the
                    // column rather than a sibling scrim so the gaps *between* rows dismiss too — the run is usually
                    // two or three apps, so most of this layer is empty space and a scrim only around it would leave
                    // the obvious place to tap doing nothing.
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.contentMuted,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                )
                apps.forEach { app ->
                    key(app.componentKey.flatten()) {
                        AppRowCell(
                            app = app,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            itemGestures = Modifier.pointerInput(app.componentKey) {
                                detectTapGestures { onLaunch(app.componentKey) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which letter the rail is showing, and how it got there — **the held one while a finger is down, and the last held
 * one after it lifts**.
 *
 * Two fields rather than one because the two answer different questions and both are needed at once: [held] is what
 * the *rail* draws as active and what a release has to survive, [shown] is what the filter draws. Collapsing them
 * would mean a lift either cleared the filter or froze the rail's highlight on a letter nobody is touching.
 *
 * A plain holder rather than the ViewModel's, for `AppsScreen`'s `AlphabetFilter` reason: a letter is about *this*
 * visit to the surface, and the surface leaving is what should end it.
 */
internal class HomeLetterFilter {

    private var heldBucket: Int? by mutableStateOf(null)
    private var settledBucket: Int? by mutableStateOf(null)

    /** The bucket under the finger, or null once it has lifted — what the rail highlights. */
    val held: Int? get() = heldBucket

    /** The bucket being filtered to, held or settled, or null when nothing is. */
    val shown: Int? get() = heldBucket ?: settledBucket

    /** The rail reporting where the finger is: a position while it is down, null as it lifts. */
    fun onLetter(bucket: Int?) {
        heldBucket = bucket
        if (bucket != null) settledBucket = bucket
    }

    /** Closes the filter — back, or a tap on the empty space around the apps. */
    fun clear() {
        heldBucket = null
        settledBucket = null
    }
}

/**
 * [HomeLetterFilter] for this surface, closed when the surface leaves.
 *
 * A letter is about *this* visit, exactly as a query is on APPS: left behind, the next time HOME comes forward it
 * shows a screenful of apps under a letter chosen for a reason that happened last time.
 */
@Composable
internal fun rememberHomeLetterFilter(presented: Boolean): HomeLetterFilter {
    val filter = remember { HomeLetterFilter() }
    LaunchedEffect(presented) { if (!presented) filter.clear() }
    return filter
}
