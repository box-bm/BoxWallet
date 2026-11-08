package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.model.BackupData
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.minorDigits
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

class UnsupportedBackupVersionException(version: Int?) :
    IllegalArgumentException("Unsupported backup version: ${version ?: "missing"} (supported: 2, ${BackupData.CURRENT_VERSION})")

/**
 * Reads backup files of every supported version into the current [BackupData] shape (spec 6.3).
 *
 * Version 2 (before multi-currency) has `Double` amounts, no currencies and no accounts: everything
 * goes to one account in the device main currency, amounts become minor units (HALF_EVEN), rates
 * are identity. Ids are kept, so category, loan and recurrence references still match.
 */
object LegacyBackupConverter {

    private const val LEGACY_VERSION = 2

    /** Id of the single account a v2 backup is restored into (the tables are empty by then). */
    const val LEGACY_ACCOUNT_ID = 1L

    private val gson = Gson()

    /** Parses [json] by its `version`; v2 is converted with [device] as main currency. */
    fun decode(json: String, device: Currency, legacyAccountName: String): BackupData {
        val root = JsonParser.parseString(json).asJsonObject
        return when (val version = root.int("version")) {
            LEGACY_VERSION -> fromV2(root, device, LEGACY_ACCOUNT_ID, legacyAccountName)
            BackupData.CURRENT_VERSION -> fromV3(root)
            else -> throw UnsupportedBackupVersionException(version)
        }
    }

    /** Gson skips Kotlin defaults and nullability: check what the restore relies on. */
    private fun fromV3(root: JsonObject): BackupData {
        val code = root.string("mainCurrencyCode") ?: throw IllegalArgumentException("Backup is missing \"mainCurrencyCode\"")
        runCatching { Currency.getInstance(code) }.getOrElse { throw IllegalArgumentException("Backup has an unknown currency: $code") }
        val data = gson.fromJson(root, BackupData::class.java)
        @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
        return data.copy(
            accounts = data.accounts ?: emptyList(),
            transactions = data.transactions ?: emptyList(),
            categories = data.categories ?: emptyList(),
            loans = data.loans ?: emptyList(),
        )
    }

    fun fromV2(json: JsonObject, main: Currency, accountId: Long, accountName: String = "Main"): BackupData {
        require(json.int("version") == LEGACY_VERSION) { "Not a version $LEGACY_VERSION backup: ${json.get("version")}" }
        val code = main.currencyCode
        val digits = main.minorDigits()
        fun JsonObject.minor(name: String): Long =
            // The Double's shortest representation: 0.30000000000000004 → 0.30 → 30.
            BigDecimal.valueOf(getValue(name).asDouble).setScale(digits, RoundingMode.HALF_EVEN).unscaledValue().longValueExact()

        val createdAt = json.long("createdAt") ?: System.currentTimeMillis()
        val identity = ExchangeRate.IDENTITY

        val transactions = json.objects("transactions").map { t ->
            val amount = t.minor("amount")
            TransactionEntity(
                id = t.getValue("id").asLong,
                accountId = accountId,
                type = t.getValue("type").asString,
                date = t.getValue("date").asLong,
                description = t.string("description") ?: "",
                categoryId = t.long("categoryId"),
                loanId = t.long("loanId"),
                installmentNumber = t.int("installmentNumber"),
                originalAmountMinor = amount,
                currencyCode = code,
                accountAmountMinor = amount,
                accountRate = identity.value,
                accountRateSource = identity.source,
                amountMinor = amount,
                mainRate = identity.value,
                mainRateSource = identity.source,
                isCleared = t.bool("isCleared"),
                hasReminder = t.bool("hasReminder"),
                reminderDate = t.long("reminderDate"),
                reminderSecondsBefore = t.long("reminderSecondsBefore"),
                isRecurrent = t.bool("isRecurrent"),
                recurrenceFrequency = t.string("recurrenceFrequency"),
                recurrenceInterval = t.int("recurrenceInterval"),
                nextOccurrenceDate = t.long("nextOccurrenceDate"),
                parentRecurrenceId = t.long("parentRecurrenceId"),
            )
        }
        val loans = json.objects("loans").map { l ->
            LoanEntity(
                id = l.getValue("id").asLong,
                name = l.getValue("name").asString,
                description = l.string("description") ?: "",
                totalAmountMinor = l.minor("totalAmount"),
                currencyCode = code,
                accountId = accountId,
                totalInstallments = l.getValue("totalInstallments").asInt,
                categoryId = l.long("categoryId"),
                startDate = l.getValue("startDate").asLong,
                loanType = l.string("loanType") ?: "EXPENSE",
                frequency = l.string("frequency") ?: "MONTHLY",
                frequencyInterval = l.int("frequencyInterval") ?: 1,
            )
        }
        val categories = json.objects("categories").map { c ->
            CategoryEntity(
                id = c.getValue("id").asLong,
                name = c.getValue("name").asString,
                description = c.string("description") ?: "",
                icon = c.string("icon") ?: "Category",
            )
        }
        val account = AccountEntity(
            id = accountId,
            name = accountName,
            type = "CHECKING",
            currencyCode = code,
            createdAt = createdAt,
        )
        return BackupData(
            version = BackupData.CURRENT_VERSION,
            createdAt = createdAt,
            mainCurrencyCode = code,
            accounts = listOf(account),
            transactions = transactions,
            categories = categories,
            loans = loans,
        )
    }

    private fun JsonObject.present(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }
    private fun JsonObject.getValue(name: String): JsonElement =
        present(name) ?: throw IllegalArgumentException("Backup is missing \"$name\"")
    private fun JsonObject.long(name: String): Long? = present(name)?.asLong
    private fun JsonObject.int(name: String): Int? = present(name)?.asInt
    private fun JsonObject.string(name: String): String? = present(name)?.asString
    private fun JsonObject.bool(name: String): Boolean = present(name)?.asBoolean ?: false
    private fun JsonObject.objects(name: String): List<JsonObject> =
        present(name)?.asJsonArray?.map { it.asJsonObject } ?: emptyList()
}
