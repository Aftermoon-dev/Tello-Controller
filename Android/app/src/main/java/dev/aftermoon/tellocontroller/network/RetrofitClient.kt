package dev.aftermoon.tellocontroller.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var retrofitClient: Retrofit? = null

    fun getRetrofitClient(baseUrl: String): Retrofit? {
        retrofitClient = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofitClient
    }
}