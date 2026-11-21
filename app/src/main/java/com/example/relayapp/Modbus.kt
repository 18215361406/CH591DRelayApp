package com.example.relayapp

/**
 * Modbus RTU 协议工具
 * 对应固件 peripheral_main.c 中的寄存器定义
 */
object Modbus {

    const val SLAVE_ADDR = 1       // modbus_addr 默认值
    const val REG_NTC_TEMP = 0     // 温度，单位 0.1℃
    const val REG_CF1_CUR = 1      // 电流，单位 mA
    const val REG_RELAY = 2        // 继电器

    /** 计算 Modbus CRC16 */
    fun crc16(data: ByteArray, len: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until len) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc shr 1) xor 0xA001
                } else {
                    crc shr 1
                }
            }
        }
        return crc
    }

    /** 构造「读多个寄存器」报文（功能码 0x03） */
    fun buildReadRegs(startReg: Int, count: Int): ByteArray {
        val frame = ByteArray(6)
        frame[0] = SLAVE_ADDR.toByte()
        frame[1] = 0x03
        frame[2] = ((startReg shr 8) and 0xFF).toByte()
        frame[3] = (startReg and 0xFF).toByte()
        frame[4] = ((count shr 8) and 0xFF).toByte()
        frame[5] = (count and 0xFF).toByte()
        val crc = crc16(frame, 6)
        val out = ByteArray(8)
        System.arraycopy(frame, 0, out, 0, 6)
        out[6] = (crc and 0xFF).toByte()
        out[7] = ((crc shr 8) and 0xFF).toByte()
        return out
    }

    /** 构造「写单个寄存器」报文（功能码 0x06） */
    fun buildWriteReg(reg: Int, value: Int): ByteArray {
        val frame = ByteArray(6)
        frame[0] = SLAVE_ADDR.toByte()
        frame[1] = 0x06
        frame[2] = ((reg shr 8) and 0xFF).toByte()
        frame[3] = (reg and 0xFF).toByte()
        frame[4] = ((value shr 8) and 0xFF).toByte()
        frame[5] = (value and 0xFF).toByte()
        val crc = crc16(frame, 6)
        val out = ByteArray(8)
        System.arraycopy(frame, 0, out, 0, 6)
        out[6] = (crc and 0xFF).toByte()
        out[7] = ((crc shr 8) and 0xFF).toByte()
        return out
    }
}
