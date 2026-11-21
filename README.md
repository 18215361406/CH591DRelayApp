# CH591D 继电器控制 App

基于安卓原生 BLE 的继电器控制 App，用于连接沁恒 CH591D 蓝牙从机，控制一路继电器并显示温度、电流。

## 功能

- 扫描并连接 `ch592_ble_uart` 设备
- 开 / 关继电器（Modbus 06 写寄存器 2）
- 实时显示温度（0.1℃）、电流（mA）（Modbus 03 读寄存器 0、1，每 2 秒刷新）

## 通信协议

对应固件 `ble_uart_service.c`：

| 项 | 值 |
|----|----|
| 服务 UUID | `6E400001-B5A3-F393-E0A9-E50E24DCCA9F` |
| 写特征（RX） | `6E400002-...` |
| 通知特征（TX） | `6E400003-...` |

Modbus 寄存器（`peripheral_main.c`）：

| 寄存器 | 含义 | 单位 |
|--------|------|------|
| 0 | NTC 温度 | 0.1℃ |
| 1 | 电流 | mA |
| 2 | 继电器 | 0/1 |

## 构建

- 本地：Android Studio 打开工程，或 `gradle assembleDebug`
- 云端：推送到 GitHub，GitHub Actions 自动编译并产出 APK artifact

## 使用

1. 手机安装 APK（允许「未知来源」安装）
2. 打开 App，授予蓝牙 / 定位权限
3. 点击「连接设备」，选择 `ch592_ble_uart`
4. 点「开 / 关」控制继电器，查看温度电流
