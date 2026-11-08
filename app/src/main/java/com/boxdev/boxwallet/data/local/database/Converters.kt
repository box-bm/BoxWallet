package com.boxdev.boxwallet.data.local.database

import androidx.room.TypeConverter
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.money.RateSource
import java.math.BigDecimal

class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromRecurrenceFrequency(frequency: RecurrenceFrequency?): String? = frequency?.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String?): RecurrenceFrequency? =
        value?.let { RecurrenceFrequency.valueOf(it) }

    @TypeConverter
    fun fromLoanType(type: LoanType): String = type.name

    @TypeConverter
    fun toLoanType(value: String): LoanType = LoanType.valueOf(value)

    /** Stored as TEXT so rates keep full precision. */
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromRateSource(source: RateSource?): String? = source?.name

    @TypeConverter
    fun toRateSource(value: String?): RateSource? = value?.let { RateSource.valueOf(it) }
}
