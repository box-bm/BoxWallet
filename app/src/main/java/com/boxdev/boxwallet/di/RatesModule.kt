package com.boxdev.boxwallet.di

import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.rates.ExchangeRateApi
import com.boxdev.boxwallet.data.rates.HttpTransport
import com.boxdev.boxwallet.data.rates.UrlConnectionTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RatesModule {

    @Provides
    @Singleton
    fun provideHttpTransport(): HttpTransport = UrlConnectionTransport()

    @Provides
    @Singleton
    fun provideExchangeRateApi(transport: HttpTransport, analytics: AnalyticsHelper): ExchangeRateApi =
        ExchangeRateApi(transport) { analytics.logEvent("rate_payload_invalid", mapOf("reason" to it)) }
}
