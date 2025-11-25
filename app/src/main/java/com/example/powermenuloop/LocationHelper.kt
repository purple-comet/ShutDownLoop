package com.example.powermenuloop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat

class LocationHelper private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        @Volatile
        private var instance: LocationHelper? = null

        fun getInstance(context: Context): LocationHelper {
            return instance ?: synchronized(this) {
                instance ?: LocationHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 現在の位置情報を取得する
     * 権限がない場合や取得できない場合はnullを返す
     */
    fun getCurrentLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 権限チェック
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted")
            return null
        }

        // 最後に取得した位置情報を確認（GPS優先、なければNetwork）
        val locGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val locNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        // より新しい位置情報を採用するロジックなどが理想だが、簡易的にGPS優先で取得
        return locGPS ?: locNet
    }

    fun getDistanceFromHome(): Float {
        val location = getCurrentLocation()

        if (location == null) {
            Log.d(TAG, "Location not found")
            // 位置情報が取れない場合はとりあえずfalse（実行しない）
            return Float.MAX_VALUE
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            Constants.HOME_LATITUDE,
            Constants.HOME_LONGITUDE,
            results
        )
        val distance = results[0]

        Log.d(TAG, "Current Distance to Home: ${distance}m")

        return distance
    }

    /**
     * 自宅付近（Constantsで設定した範囲内）にいるかどうか判定する
     */
    fun isNearHome(): Boolean {
        val distance = getDistanceFromHome()

        return distance <= Constants.RADIUS_METERS
    }
}
