package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PayoutApiRequest(
    val amountCents: Int,
    val paypalEmail: String,
    val currency: String = "USD",
)

@JsonClass(generateAdapter = true)
data class PayoutApiResponse(
    val payoutId: String? = null,
    val payoutBatchId: String? = null,
    val status: String? = null,
    val amountCents: Int? = null,
    val currency: String? = null,
    val error: String? = null,
)

interface PayPalPayoutApiService {
    @POST("requestPayPalPayout")
    suspend fun requestPayPalPayout(
        @Header("Authorization") bearerToken: String,
        @Body request: PayoutApiRequest,
    ): Response<PayoutApiResponse>

    companion object {
        fun create(baseUrl: String): PayPalPayoutApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(PayPalPayoutApiService::class.java)
        }
    }
}
