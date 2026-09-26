package com.simivr.app.di

import android.content.Context
import androidx.room.Room
import com.simivr.app.data.SimIvrDatabase
import com.simivr.app.data.dao.CallLogDao
import com.simivr.app.data.dao.CallerRuleDao
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.dao.ContactDao
import com.simivr.app.data.dao.DndDao
import com.simivr.app.data.dao.FlowDao
import com.simivr.app.data.dao.IncomingLineDao
import com.simivr.app.data.dao.OutboxDao
import com.simivr.app.sync.CrmApi
import com.simivr.app.sync.DynamicBaseUrlInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Plain (unqualified) [Context] binding backed by the application context, so the many
     * `@Inject constructor(private val context: Context)` classes throughout the app (Player,
     * TtsEngine, MicCapture, repos, etc.) don't each need an `@ApplicationContext` qualifier. */
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SimIvrDatabase =
        Room.databaseBuilder(context, SimIvrDatabase::class.java, SimIvrDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFlowDao(db: SimIvrDatabase): FlowDao = db.flowDao()

    @Provides
    fun provideIncomingLineDao(db: SimIvrDatabase): IncomingLineDao = db.incomingLineDao()

    @Provides
    fun provideCallerRuleDao(db: SimIvrDatabase): CallerRuleDao = db.callerRuleDao()

    @Provides
    fun provideCampaignDao(db: SimIvrDatabase): CampaignDao = db.campaignDao()

    @Provides
    fun provideContactDao(db: SimIvrDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideCallLogDao(db: SimIvrDatabase): CallLogDao = db.callLogDao()

    @Provides
    fun provideOutboxDao(db: SimIvrDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideDndDao(db: SimIvrDatabase): DndDao = db.dndDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // Placeholder host: DynamicBaseUrlInterceptor rewrites scheme/host/port from Settings on every request.
            .baseUrl("http://sim-ivr.local/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideCrmApi(retrofit: Retrofit): CrmApi = retrofit.create(CrmApi::class.java)
}
