package inkspire.morphic.feature.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import inkspire.morphic.core.designsystem.theme.LocalMorphicColors
import inkspire.morphic.data.billing.BillingPeriod
import inkspire.morphic.data.billing.FreeTrial
import inkspire.morphic.data.billing.SubscriptionPlan
import inkspire.morphic.data.billing.TrialUnit

/**
 * One plan to pick, in a group where exactly one is selected.
 *
 * @param savingPercent the yearly plan's saving over twelve months of the monthly one, or null to show no badge.
 */
@Composable
internal fun PlanOption(
    plan: SubscriptionPlan,
    selected: Boolean,
    savingPercent: Int?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMorphicColors.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.outline,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.contentMuted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(plan.period.title, style = MaterialTheme.typography.titleMedium, color = colors.content)
            Text(
                text = plan.freeTrial?.let { "${it.label}, then ${plan.pricePerPeriod}" } ?: plan.pricePerPeriod,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
            )
        }
        if (savingPercent != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Save $savingPercent%",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

internal val BillingPeriod.title: String
    get() = when (this) {
        BillingPeriod.MONTHLY -> "Monthly"
        BillingPeriod.YEARLY -> "Yearly"
    }

/** "€2.99 / month" — Play's formatted price, with the period it renews on. Non-breaking, so it never wraps apart. */
internal val SubscriptionPlan.pricePerPeriod: String
    get() = when (period) {
        BillingPeriod.MONTHLY -> "$price / month"
        BillingPeriod.YEARLY -> "$price / year"
    }

/** "7-day free trial". */
internal val FreeTrial.label: String
    get() {
        val unit = when (unit) {
            TrialUnit.DAY -> "day"
            TrialUnit.WEEK -> "week"
            TrialUnit.MONTH -> "month"
            TrialUnit.YEAR -> "year"
        }
        return "$count-$unit free trial"
    }
