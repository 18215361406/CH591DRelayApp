package com.example.relayapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * BLE 设备扫描选择页
 */
@SuppressLint("MissingPermission")
class DeviceScanActivity : AppCompatActivity() {

    private lateinit var adapter: ArrayAdapter<String>
    private val devices = mutableListOf<BluetoothDevice>()
    private val btAdapter: BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val scanCallback = object : BluetoothAdapter.LeScanCallback {
        override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
            runOnUiThread {
                if (!devices.contains(device)) {
                    devices.add(device)
                    val name = device.name ?: "未知设备"
                    adapter.add("$name  (${device.address})  ${rssi}dBm")
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        val tvHint = findViewById<TextView>(R.id.tvHint)
        val list = findViewById<ListView>(R.id.deviceList)

        tvHint.text = "正在扫描 BLE 设备，请选择 ${BleManager.DEVICE_NAME}"

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            val device = devices[position]
            val intent = Intent().apply {
                putExtra("device", device)
            }
            setResult(RESULT_OK, intent)
            finish()
        }

        btAdapter.startLeScan(scanCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { btAdapter.stopLeScan(scanCallback) } catch (_: Exception) {}
    }
}
