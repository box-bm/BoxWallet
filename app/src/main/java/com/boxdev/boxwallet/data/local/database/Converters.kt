package com.boxdev.boxwallet.data.local.database

import androidx.room.TypeConverter
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency

class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun fromRecurrenceFrequency(frequency: RecurrenceFrequency?): String? {
        return frequency?.name
    }

    @TypeConverter
    fun toRecurrenceFrequency(value: String?): RecurrenceFrequency? {
        return value?.let { RecurrenceFrequency.valueOf(it) }
    }

    @TypeConverter
    fun fromLoanType(type: LoanType): String {
        return type.name
    }

    @TypeConverter
    fun toLoanType(value: String): LoanType {
        return LoanType.valueOf(value)
    }
}
