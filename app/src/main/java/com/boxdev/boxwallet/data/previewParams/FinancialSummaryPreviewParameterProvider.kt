package com.boxdev.boxwallet.data.previewParams

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.boxdev.boxwallet.domain.model.FinancialSummary

class FinancialSummaryPreviewParameterProvider : PreviewParameterProvider<FinancialSummary> {
    override val values = sequenceOf(
        FinancialSummary(1200.0, 2000.0, 200.0, 100.0),
        FinancialSummary(3000.0, 2000.0, 200.0, 100.0)
    )
}