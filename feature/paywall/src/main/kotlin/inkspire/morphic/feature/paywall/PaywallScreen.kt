package inkspire.morphic.feature.paywall

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import inkspire.morphic.core.designsystem.component.button.MorphicButton
import inkspire.morphic.core.designsystem.component.button.MorphicButtonStyle
import inkspire.morphic.core.designsystem.insets.uiInsets
import inkspire.morphic.core.designsystem.insets.uiInsetsPadding
import inkspire.morphic.core.designsystem.theme.LauncherTheme
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.BillingProblem
import inkspire.morphic.data.billing.Entitlement
import inkspire.morphic.data.billing.SubscriptionPlans
import org.koin.androidx.compose.koinViewModel

/**
 * The subscription's purchase screen: pick monthly or yearly, and buy through Play.
 *
 * **It lists no benefits yet**, because what the subscription unlocks has not been decided. A list written now would
 * be a promise the app does not keep.
 *
 * **Re-read on every resume**, since the subscription changes in Play's own screens — the purchase sheet, and the
 * subscription manager this links to — and nothing reports back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<PaywallViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    BackHandler(onBack = onBack)

    LauncherTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalMorphicColors.current
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                    windowInsets = uiInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .uiInsetsPadding(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text("Morphic Premium", style = MaterialTheme.typography.headlineMedium, color = colors.content)
                PaywallBody(
                    state = state,
                    onSelect = viewModel::select,
                    onSubscribe = { activity -> viewModel.subscribe(activity) },
                    onRetry = viewModel::refresh,
                )
            }
        }
    }
}

/** What is shown below the title: the entitlement if the user has one, otherwise what is for sale. */
@Composable
private fun ColumnScope.PaywallBody(
    state: PaywallState,
    onSelect: (BillingPeriod) -> Unit,
    onSubscribe: (Activity) -> Unit,
    onRetry: () -> Unit,
) {
    when (val entitlement = state.entitlement) {
        is Entitlement.Active -> Subscribed(entitlement)
        Entitlement.Pending -> Notice(
            "Your purchase is waiting for payment to clear. It unlocks as soon as Google Play confirms it.",
        )
        else -> when (val plans = state.plans) {
            SubscriptionPlans.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            }
            is SubscriptionPlans.Unavailable -> Unavailable(plans.problem, onRetry)
            is SubscriptionPlans.Ready -> PlanPicker(state, plans, onSelect, onSubscribe)
        }
    }
}

@Composable
private fun ColumnScope.PlanPicker(
    state: PaywallState,
    plans: SubscriptionPlans.Ready,
    onSelect: (BillingPeriod) -> Unit,
    onSubscribe: (Activity) -> Unit,
) {
    val colors = LocalMorphicColors.current
    val activity = LocalActivity.current
    val monthly = plans.plans.firstOrNull { it.period == BillingPeriod.MONTHLY }
    plans.plans.forEach { plan ->
        PlanOption(
            plan = plan,
            selected = plan.period == state.selected,
            savingPercent = if (plan.period == BillingPeriod.YEARLY && monthly != null) {
                yearlySavingPercent(monthly, plan)
            } else {
                null
            },
            onSelect = { onSelect(plan.period) },
        )
    }
    val chosen = state.selectedPlan ?: return
    // Absent rather than disabled without an Activity: Play's sheet has nothing to open over.
    if (activity != null) {
        MorphicButton(
            onClick = { onSubscribe(activity) },
            enabled = !state.opening,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (chosen.freeTrial != null) "Start free trial" else "Subscribe")
        }
    }
    if (state.launchFailed) {
        Text("Couldn't open Google Play. Try again.", style = MaterialTheme.typography.bodyMedium, color = colors.error)
    }
    Text(
        text = buildString {
            if (chosen.freeTrial != null) append("${chosen.pricePerPeriod} after the trial. ")
            append("Renews automatically until canceled. Cancel anytime in Google Play.")
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.contentMuted,
    )
}

@Composable
private fun Subscribed(entitlement: Entitlement.Active) {
    val uriHandler = LocalUriHandler.current
    Notice(
        if (entitlement.autoRenewing) {
            "You're subscribed. Thank you."
        } else {
            "Your subscription is canceled and stays active until the end of the period you paid for."
        },
    )
    MorphicButton(
        onClick = { uriHandler.openUri(ManageSubscriptionsUrl) },
        style = MorphicButtonStyle.Outlined,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Manage subscription")
    }
}

/** Retry is offered only where trying again can help: a network problem or an unexplained error. */
@Composable
private fun Unavailable(problem: BillingProblem, onRetry: () -> Unit) {
    Notice(
        when (problem) {
            BillingProblem.NO_PLAY_BILLING -> "Subscriptions need Google Play, which isn't available on this device."
            BillingProblem.NOT_FOR_SALE -> "Subscriptions aren't available yet."
            BillingProblem.NETWORK -> "Couldn't reach Google Play. Check your connection."
            BillingProblem.ERROR -> "Google Play couldn't load the subscription."
        },
    )
    if (problem == BillingProblem.NETWORK || problem == BillingProblem.ERROR) {
        MorphicButton(onClick = onRetry, style = MorphicButtonStyle.Outlined, modifier = Modifier.fillMaxWidth()) {
            Text("Try again")
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = LocalMorphicColors.current.content)
}

/** Play's own subscription manager. Without a product in the URL it lists every subscription, which is still right. */
private const val ManageSubscriptionsUrl = "https://play.google.com/store/account/subscriptions"
