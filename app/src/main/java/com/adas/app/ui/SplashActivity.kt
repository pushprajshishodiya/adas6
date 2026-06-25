package com.adas.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.adas.app.vehicle.VehicleProfile

class SplashActivity : AppCompatActivity() {
    private val PERMS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    private val REQ   = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (allGranted()) proceed() else ActivityCompat.requestPermissions(this, PERMS, REQ)
    }

    private fun proceed() {
        val next = if (VehicleProfile.isConfigured(this)) DashboardActivity::class.java
                   else SetupActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (allGranted()) proceed()
    }

    private fun allGranted() = PERMS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
