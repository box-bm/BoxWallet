package com.boxdev.boxwallet.data.local.database

import androidx.room.TypeConverter
import com.boxdev.boxwallet.data.local.entity.TransactionType

class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }
}
