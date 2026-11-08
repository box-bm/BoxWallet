package com.boxdev.boxwallet.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.boxdev.boxwallet.data.rates.MainCurrencyChanger
import com.boxdev.boxwallet.domain.money.MissingRateException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.Currency
import java.util.UUID

/**
 * Runs a main currency change (spec 4): phase 1 downloads missing rates (cancellable), phase 2
 * rewrites every movement in one transaction (not cancellable). Progress: [KEY_PHASE], [KEY_DONE],
 * [KEY_TOTAL]. Output: the summary counts, or [KEY_ERROR] on failure.
 */
@HiltWorker
class MainCurrencyChangeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val changer: MainCurrencyChanger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val code = inputData.getString(KEY_CURRENCY) ?: return Result.failure(error(ERROR_UNKNOWN))
        val to = runCatching { Currency.getInstance(code) }.getOrNull() ?: return Result.failure(error(ERROR_UNKNOWN))

        setProgress(progress(PHASE_DOWNLOAD, 0, 0))
        val fetched = changer.prefetch(to, throttled(PHASE_DOWNLOAD))
        if (!fetched) return Result.failure(error(ERROR_NETWORK))

        setProgress(progress(PHASE_APPLY, 0, 0))
        return try {
            val summary = changer.apply(to, throttled(PHASE_APPLY))
            Result.success(
                workDataOf(
                    KEY_CONVERTED to summary.converted,
                    KEY_APPROXIMATE to summary.approximate,
                    KEY_MANUAL_REPLACED to summary.manualReplaced,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: MissingRateException) {
            Result.failure(error(ERROR_MISSING_RATE))
        } catch (_: Exception) {
            Result.failure(error(ERROR_UNKNOWN))
        }
    }

    /** Reports at most ~100 steps so a large change doesn't flood WorkManager's database. */
    private fun throttled(phase: String): (Int, Int) -> Unit {
        var lastStep = -1
        return { done, total ->
            val step = if (total == 0) 100 else done * 100 / total
            if (step != lastStep) {
                lastStep = step
                setProgressAsync(progress(phase, done, total))
            }
        }
    }

    private fun progress(phase: String, done: Int, total: Int) =
        workDataOf(KEY_PHASE to phase, KEY_DONE to done, KEY_TOTAL to total)

    private fun error(reason: String): Data = workDataOf(KEY_ERROR to reason)

    companion object {
        const val WORK_NAME = "main_currency_change"
        const val KEY_CURRENCY = "currency"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CONVERTED = "converted"
        const val KEY_APPROXIMATE = "approximate"
        const val KEY_MANUAL_REPLACED = "manualReplaced"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_APPLY = "apply"
        const val ERROR_NETWORK = "network"
        const val ERROR_MISSING_RATE = "missing_rate"
        const val ERROR_UNKNOWN = "unknown"

        /** Starts the change unless one is already running; returns the id of the run to follow. */
        fun enqueue(context: Context, to: Currency): UUID {
            val request = OneTimeWorkRequestBuilder<MainCurrencyChangeWorker>()
                .setInputData(workDataOf(KEY_CURRENCY to to.currencyCode))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return request.id
        }

        /** Cancels the change; the changer ignores it once the transaction phase has started. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
