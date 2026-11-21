package com.example.relayapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * BLE 连接管理器
 * 对应固件 ble_uart_service.c 中的 GATT 定义
 */
class BleManager(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9f")
        val RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9f") // 写
        val TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9f") // 通知
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEVICE_NAME = "ch592_ble_uart"
    }

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(bytes: ByteArray)
        fun onLog(msg: String)
    }

    var listener: Listener? = null
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null

    val isConnected: Boolean
        get() = gatt?.let { it.getService(SERVICE_UUID) != null } == true &&
                (gatt?.let { ble -> ble.connectedDevices?.any { dev -> ble.getConnectionState(dev) == BluetoothProfile.STATE_CONNECTED } } == true)

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener?.onLog("已连接，正在发现服务…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener?.onLog("连接断开")
                    listener?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service: BluetoothGattService? = g.getService(SERVICE_UUID)
                if (service == null) {
                    listener?.onLog("未找到目标服务")
                    return
                }
                rxChar = service.getCharacteristic(RX_CHAR_UUID)
                txChar = service.getCharacteristic(TX_CHAR_UUID)
                if (rxChar == null || txChar == null) {
                    listener?.onLog("未找到特征")
                    return
                }
                // 订阅通知
                enableNotify(txChar!!)
                listener?.onLog("服务就绪")
                listener?.onConnected()
            } else {
                listener?.onLog("发现服务失败: $status")
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { listener?.onDataReceived(it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        listener?.onLog("连接中: ${device.name ?: device.address}")
        gatt = device.connectGatt(context, false, callback)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(char: BluetoothGattCharacteristic) {
        gatt?.let { g ->
            val ok = g.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CCCD_UUID)
            descriptor?.let { d ->
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
            listener?.onLog("订阅通知: $ok")
        }
    }

    @SuppressLint("MissingPermission")
    fun write(bytes: ByteArray): Boolean {
        val char = rxChar ?: run {
            listener?.onLog("未就绪，无法写入")
            return false
        }
        // 固件 RX 特征属性为 WRITE_NO_RSP，用无响应写
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        char.value = bytes
        val ok = gatt?.writeCharacteristic(char) == true
        if (!ok) listener?.onLog("写入失败")
        return ok
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        rxChar = null
        txChar = null
    }
}
