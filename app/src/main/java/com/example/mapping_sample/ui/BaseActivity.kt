package com.example.mapping_sample.ui

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mapping_sample.ui.dashboard.DashboardFragment
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

open class BaseActivity : AppCompatActivity(){
    private val context = this

    // define
    private val Context.dataStore by preferencesDataStore(name = "settings")
    // datastore read
//    private val exampleCounterFlow: Flow<Int> = context.dataStore.data
//        .map { preferences ->
//            // No type safety.
//            preferences[EXAMPLE_COUNTER] ?: 0
//        }
    // datastore write
    private suspend fun incrementCounter() {
        context.dataStore.edit { settings ->
            val currentCounterValue = settings[EXAMPLE_COUNTER] ?: 0
            settings[EXAMPLE_COUNTER] = currentCounterValue + 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 非同期実行
        GlobalScope.async {
            incrementCounter()
        }

        // カメラ権限
        grantCameraPermission()
    }

    private fun grantCameraPermission() =
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.CAMERA),
            DashboardFragment.CAMERA_PERMISSION_REQUEST_CODE)

    // static field
    companion object {
        private val EXAMPLE_COUNTER = intPreferencesKey("example_counter")
    }
}