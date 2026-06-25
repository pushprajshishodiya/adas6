package com.adas.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.adas.app.R
import com.adas.app.vehicle.VehicleProfile

class SetupActivity : AppCompatActivity() {
    private lateinit var etName:       EditText
    private lateinit var etMake:       EditText
    private lateinit var etModel:      EditText
    private lateinit var etYear:       EditText
    private lateinit var etLength:     EditText
    private lateinit var etWidth:      EditText
    private lateinit var etWheelbase:  EditText
    private lateinit var etCamHeight:  EditText
    private lateinit var etCamFov:     EditText
    private lateinit var etSteerRatio: EditText
    private lateinit var etTurnRadius: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        etName=findViewById(R.id.etVehicleName); etMake=findViewById(R.id.etMake)
        etModel=findViewById(R.id.etModel); etYear=findViewById(R.id.etYear)
        etLength=findViewById(R.id.etLength); etWidth=findViewById(R.id.etWidth)
        etWheelbase=findViewById(R.id.etWheelbase); etCamHeight=findViewById(R.id.etCamHeight)
        etCamFov=findViewById(R.id.etCamFov); etSteerRatio=findViewById(R.id.etSteerRatio)
        etTurnRadius=findViewById(R.id.etTurnRadius)
        fillFrom(VehicleProfile.load(this))
        findViewById<Button>(R.id.btnPresetSedan).setOnClickListener {
            fillFrom(VehicleProfile(name="Sedan",lengthM=4.5f,widthM=1.8f,wheelbaseM=2.7f,cameraHeightM=1.1f,steeringRatioFull=16f,turningRadiusM=5.5f)) }
        findViewById<Button>(R.id.btnPresetSuv).setOnClickListener {
            fillFrom(VehicleProfile(name="SUV",lengthM=4.7f,widthM=1.9f,wheelbaseM=2.8f,cameraHeightM=1.4f,steeringRatioFull=15f,turningRadiusM=6.0f)) }
        findViewById<Button>(R.id.btnPresetTruck).setOnClickListener {
            fillFrom(VehicleProfile(name="Truck",lengthM=5.5f,widthM=2.0f,wheelbaseM=3.2f,cameraHeightM=1.6f,steeringRatioFull=18f,turningRadiusM=7.5f)) }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            VehicleProfile.save(this, buildProfile()); goToDashboard() }
        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            if (!VehicleProfile.isConfigured(this)) VehicleProfile.save(this, VehicleProfile())
            goToDashboard() }
    }
    private fun fillFrom(p: VehicleProfile) {
        etName.setText(p.name); etMake.setText(p.make); etModel.setText(p.model)
        etYear.setText(p.year.toString()); etLength.setText(p.lengthM.toString())
        etWidth.setText(p.widthM.toString()); etWheelbase.setText(p.wheelbaseM.toString())
        etCamHeight.setText(p.cameraHeightM.toString()); etCamFov.setText(p.cameraFovDeg.toString())
        etSteerRatio.setText(p.steeringRatioFull.toString()); etTurnRadius.setText(p.turningRadiusM.toString())
    }
    private fun buildProfile() = VehicleProfile(
        name=etName.text.toString().ifBlank{"My Car"}, make=etMake.text.toString(),
        model=etModel.text.toString(), year=etYear.text.toString().toIntOrNull()?:2020,
        lengthM=etLength.text.toString().toFloatOrNull()?:4.5f, widthM=etWidth.text.toString().toFloatOrNull()?:1.8f,
        wheelbaseM=etWheelbase.text.toString().toFloatOrNull()?:2.7f,
        cameraHeightM=etCamHeight.text.toString().toFloatOrNull()?:1.2f,
        cameraFovDeg=etCamFov.text.toString().toFloatOrNull()?:70f,
        steeringRatioFull=etSteerRatio.text.toString().toFloatOrNull()?:16f,
        turningRadiusM=etTurnRadius.text.toString().toFloatOrNull()?:5.5f)
    private fun goToDashboard() { startActivity(Intent(this, DashboardActivity::class.java)); finish() }
}
