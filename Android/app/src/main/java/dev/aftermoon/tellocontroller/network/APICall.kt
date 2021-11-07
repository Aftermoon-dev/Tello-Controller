package dev.aftermoon.tellocontroller.network

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface APICall {
    @GET("takeoff")
    fun takeOff(): Call<BaseResponse>

    @GET("land")
    fun land(): Call<BaseResponse>

    @GET("forward")
    fun forward(
        @Query("distance") distance: Int
    ): Call<BaseResponse>

    @GET("back")
    fun back(
        @Query("distance") distance: Int
    ): Call<BaseResponse>

    @GET("left")
    fun left(
        @Query("distance") distance: Int
    ): Call<BaseResponse>

    @GET("right")
    fun right(
        @Query("distance") distance: Int
    ): Call<BaseResponse>

    @GET("rotate_cw")
    fun rotate_cw(
        @Query("angle") angle: Int
    ): Call<BaseResponse>

    @GET("rotate_ccw")
    fun rotate_ccw(
        @Query("angle") angle: Int
    ): Call<BaseResponse>

    @GET("up")
    fun up(
        @Query("distance") distance: Int
    ): Call<BaseResponse>

    @GET("down")
    fun down(
        @Query("distance") distance: Int
    ): Call<BaseResponse>

    @GET("speed")
    fun speed(
        @Query("speed") speed: Int
    ): Call<BaseResponse>

    @GET("capture")
    fun capture(): Call<BaseResponse>

    @GET("getcapture")
    @Streaming
    fun getcapture(): Call<ResponseBody>

    @GET("stop")
    fun stop(): Call<BaseResponse>

    @GET("emergency")
    fun emergency(): Call<BaseResponse>
}