package com.example.relayapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bleManager: BleManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var btnScan: Button
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvRelay: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvCur: TextView
    private lateinit var tvLog: TextView

    private var relayOn = false
    private var pollRunnable: Runnable? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            startScan()
        } else {
            toast("需要蓝牙和定位权限才能扫描设备")
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> startScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleManager = BleManager(this)

        btnScan = findViewById(R.id.btnScan)
        btnOn = findViewById(R.id.btnOn)
        btnOff = findViewById(R.id.btnOff)
        tvStatus = findViewById(R.id.tvStatus)
        tvRelay = findViewById(R.id.tvRelay)
        tvTemp = findViewById(R.id.tvTemp)
        tvCur = findViewById(R.id.tvCur)
        tvLog = findViewById(R.id.tvLog)

        btnScan.setOnClickListener {
            if (bleManager.isConnected) {
                disconnect()
            } else {
                checkAndScan()
            }
        }
        btnOn.setOnClickListener { setRelay(true) }
        btnOff.setOnClickListener { setRelay(false) }

        bleManager.listener = object : BleManager.Listener {
            override fun onConnected() {
                handler.post {
                    updateStatus("已连接")
                    btnScan.text = "断开连接"
                    btnOn.isEnabled = true
                    btnOff.isEnabled = true
                    startPolling()
                }
            }

            override fun onDisconnected() {
                handler.post {
                    updateStatus("未连接")
                    btnScan.text = "连接设备"
                    btnOn.isEnabled = false
                    btnOff.isEnabled = false
                    tvRelay.text = "未知"
                    tvRelay.setBackgroundColor(0xFFCCCCCC.toInt())
                    stopPolling()
                }
            }

            override fun onDataReceived(bytes: ByteArray) {
                handler.post { parseResponse(bytes) }
            }

            override fun onLog(msg: String) {
                handler.post { appendLog(msg) }
            }
        }
    }

    private fun checkAndScan() {
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            val missing = perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing) {
                permissionLauncher.launch(perms)
                return
            }
        } else {
            val missing = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            if (missing) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                return
            }
        }
        startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        appendLog("请从下方系统弹窗选择设备: ${BleManager.DEVICE_NAME}")
        toast("正在扫描，请选择设备")
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        // 通过系统扫描页选择设备（简洁可靠）
        val intent = Intent(this, DeviceScanActivity::class.java)
        startActivityForResult(intent, 100)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra("device", android.bluetooth.BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra("device")
            }
            device?.let { bleManager.connect(it) }
        }
    }

    private fun setRelay(on: Boolean) {
        val pkt = Modbus.buildWriteReg(Modbus.REG_RELAY, if (on) 1 else 0)
        if (bleManager.write(pkt)) {
            relayOn = on
            updateRelayUI(on)
            appendLog("已发送: ${if (on) "开" else "关"}继电器")
        }
    }

    private fun startPolling() {
        stopPolling()
        pollRunnable = object : Runnable {
            override fun run() {
                readData()
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(pollRunnable!!)
        readData()
    }

    private fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun readData() {
        val pkt = Modbus.buildReadRegs(Modbus.REG_NTC_TEMP, 2)
        bleManager.write(pkt)
    }

    private fun parseResponse(bytes: ByteArray) {
        // Modbus 03 应答: addr | 0x03 | byteCount | data | crc
        if (bytes.size >= 7 && bytes[1] == 0x03.toByte()) {
            val byteCount = bytes[2].toInt() and 0xFF
            if (bytes.size >= 3 + byteCount && byteCount >= 4) {
                val tempRaw = ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
                val curRaw = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)
                tvTemp.text = String.format("%.1f ℃", tempRaw / 10.0)
                tvCur.text = String.format("%d mA", curRaw)
            }
        }
        // Modbus 06 应答: addr | 0x06 | regH | regL | valH | valL | crc
        else if (bytes.size >= 6 && bytes[1] == 0x06.toByte()) {
            val reg = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            val value = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
            if (reg == Modbus.REG_RELAY) {
                updateRelayUI(value != 0)
            }
        }
    }

    private fun updateRelayUI(on: Boolean) {
        tvRelay.text = if (on) "开" else "关"
        tvRelay.setBackgroundColor(if (on) 0xFF0F6E56.toInt() else 0xFFCCCCCC.toInt())
    }

    private fun updateStatus(s: String) {
        tvStatus.text = s
    }

    private fun appendLog(msg: String) {
        val t = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        tvLog.append("$t  $msg\n")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun disconnect() {
        bleManager.disconnect()
        updateStatus("未连接")
        btnScan.text = "连接设备"
        btnOn.isEnabled = false
        btnOff.isEnabled = false
        stopPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}
