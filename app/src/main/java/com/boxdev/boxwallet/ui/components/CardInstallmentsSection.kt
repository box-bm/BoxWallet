@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.InstallmentPlan
import com.boxdev.boxwallet.domain.model.isFinished
import com.boxdev.boxwallet.domain.model.perClosing
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.LocalDateTime
import java.util.Currency

/**
 * A card's installment purchases under its statement, as one connected group; the header shows what is
 * still to be charged (spec 5.3). Finished plans fold away
 * behind "Show finished"; nothing shows without any plan. Tapping a plan opens its detail.
 */
@Composable
fun CardInstallmentsSection(
    plans: List<InstallmentPlan>,
    categories: Map<Long, Category>,
    onOpenPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plans.isEmpty()) return
    var showFinished by rememberSaveable { mutableStateOf(false) }
    val hasFinished = plans.any { it.isFinished }
    val shown = if (showFinished) plans else plans.filterNot { it.isFinished }
    val formatter = rememberCurrencyFormatter()
    // What is still to be charged, per currency (the card's and its second one's).
    val pending = plans.filter { it.pendingMinor > 0 }
        .groupBy { it.accountTotal.currency }
        .map { (currency, rows) -> formatter.format(Money(rows.sumOf { it.pendingMinor }, currency)) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" + ")
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.card_installments_title),
            trailing = pending,
            actionLabel = if (hasFinished) {
                stringResource(if (showFinished) R.string.installments_hide_finished else R.string.installments_show_finished)
            } else null,
            onAction = { showFinished = !showFinished },
        )
        shown.forEachIndexed { index, plan ->
            InstallmentPlanRow(
                plan = plan,
                category = plan.categoryId?.let(categories::get),
                index = index,
                count = shown.size,
                onClick = { onOpenPlan(plan.id) },
            )
        }
    }
}

/** "3/12 · Q100.00 per statement · Q900.00 left". */
@Composable
fun installmentProgress(plan: InstallmentPlan): String {
    val formatter = rememberCurrencyFormatter()
    return stringResource(
        R.string.installment_progress,
        plan.charged,
        plan.installments,
        formatter.format(plan.perClosing),
        formatter.format(Money(plan.pendingMinor, plan.accountTotal.currency)),
    )
}

@Composable
private fun InstallmentPlanRow(
    plan: InstallmentPlan,
    category: Category?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = BoxTheme.cardColor),
        interactionSource = interaction,
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
        leadingContent = {
            CategoryBadge(
                category = category,
                fallbackIcon = Icons.Outlined.CreditCard,
                fallbackContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                fallbackContent = MaterialTheme.colorScheme.onSurfaceVariant,
                interactionSource = interaction,
            )
        },
        supportingContent = { Text(installmentProgress(plan), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            AmountText(amount = plan.original, signed = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Text(
            plan.description.ifBlank { category?.name ?: stringResource(R.string.installment_plan_title) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CardInstallmentsPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val gtq = Currency.getInstance("GTQ")
            fun plan(id: Long, name: String, total: Long, n: Int, charged: Int) = InstallmentPlan(
                id = id, accountId = 1, description = name, categoryId = null,
                original = Money(total, gtq), accountTotal = Money(total, gtq), installments = n,
                purchaseDate = LocalDateTime.of(2026, 9, 2, 10, 0), charged = charged,
                pendingMinor = total / n * (n - charged),
            )
            CardInstallmentsSection(
                plans = listOf(plan(1, "Televisor", 12_000_00, 12, 3), plan(2, "Lavadora", 4_500_00, 3, 1), plan(3, "Zapatos", 900_00, 3, 3)),
                categories = emptyMap(),
                onOpenPlan = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Installments", widthDp = 411)
@Composable
private fun CardInstallmentsPreview() = CardInstallmentsPreviewContent()

@Preview(name = "Installments, dark", widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardInstallmentsDarkPreview() = CardInstallmentsPreviewContent()
