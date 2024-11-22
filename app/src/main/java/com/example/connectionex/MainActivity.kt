package com.example.connectionex

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.example.connectionex.databinding.ActivityMainBinding
import com.example.connector.bluetooth.ControllerBT
import com.example.connector.common.BaseController
import com.example.connector.common.ConnectionUtil.ConnectionType
import com.example.connector.serial.ControllerSerial
import com.example.connector.wifi.ControllerWiFi
import com.example.connector.usb.ControllerUSB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val controllerWiFi = ControllerWiFi()
    private val controllerUSB = ControllerUSB()
    private val controllerBT = ControllerBT()
    private val controllerSerial = ControllerSerial(this)

    private var usbDevice: UsbDevice? = null
    private var bluetoothDevice: BluetoothDevice? = null

    private val ACTION_USB_PERMISSION = "com.example.connectorex.USB_PERMISSION"

    private val usbSerialReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    handleUsbDevice(intent)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbDevice = null
                    binding.etSerialDevice.setText("")

                    disconnectAll()
                }
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    handleBluetoothDevice(intent)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    bluetoothDevice = null
                    binding.etBluetoothDevice.setText("")

                    disconnectAll()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val usbStatusFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbSerialReceiver, usbStatusFilter)

        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, bluetoothFilter)
    }

    override fun onStop() {
        super.onStop()

        unregisterReceiver(usbSerialReceiver)
        unregisterReceiver(bluetoothReceiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.btnWifiConnect.setOnClickListener {
            tryConnectToWIFI()
        }

        binding.btnUsbConnect.setOnClickListener {
            tryConnectToUSB()
        }

        binding.btnBluetoothListen.setOnClickListener {
            listenBT()
        }

        binding.btnBluetoothConnect.setOnClickListener {
            tryConnectToBT()
        }

        binding.btnSerialConnect.setOnClickListener {
            tryConnectToSerial()
        }

        binding.btnDisconnect.setOnClickListener {
            disconnectAll()
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString()
            sendMessageToTarget(message)
        }

        requestBluetoothPermissions()
    }

    private fun tryConnectToWIFI() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                val ip = binding.etWifiIP.text.toString()
                val port = binding.etWifiPort.text.toString().toInt()

                if (controllerWiFi.connectTo(ip, port)) {
                    updateLogView("[WiFi] Connected!\n")
                    startReceivingData(controllerWiFi)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryConnectToUSB() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                val port = binding.etUsbPort.text.toString().toInt()

                if (controllerUSB.connectTo(port)) {
                    updateLogView("[USB] Connected!\n")
                    startReceivingData(controllerUSB)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun listenBT() {
        CoroutineScope(Dispatchers.IO).launch {
            updateLogView("[Bluetooth Socket] Listening for connections...\n")
            if (controllerBT.listenTo()) {
                updateLogView("[Bluetooth Socket] Connected!\n")
                startReceivingData(controllerBT)
            }
        }
    }

    private fun tryConnectToBT() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                if (bluetoothDevice == null) return@launch
                if (controllerBT.connectTo(bluetoothDevice!!)) {
                    updateLogView("[Bluetooth Socket] Connected!\n")
                    startReceivingData(controllerBT)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryConnectToSerial() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                if (usbDevice == null) return@launch
                if (controllerSerial.connectTo(usbDevice!!)) {
                    updateLogView("[Serial] Connected!\n")
                    startReceivingData(controllerSerial)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startReceivingData(controller: BaseController) {
        CoroutineScope(Dispatchers.IO).launch {
            while (controller.connectionType != ConnectionType.NONE) {
                val receivedData = controller.receiveData()
                receivedData?.let {
                    val receivedMessage = String(it).trim()
                    updateLogView("Received: $receivedMessage\n")
                }
                delay(500)
            }
        }
    }

    private fun disconnectAll() {
        controllerWiFi.terminateConnection()
        controllerUSB.terminateConnection()
        controllerBT.terminateConnection()
        controllerSerial.terminateConnection()

        updateLogView("Disconnected All!\n")
    }

    private fun sendMessageToTarget(message: String) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                when {
                    controllerWiFi.connectionType == ConnectionType.WIFI -> {
                        if (controllerWiFi.sendData(message.toByteArray())) {
                            updateLogView("Sent : $message\n")
                        }
                    }
                    controllerUSB.connectionType == ConnectionType.USB -> {
                        if (controllerUSB.sendData(message.toByteArray())) {
                            updateLogView("Sent : $message\n")
                        }
                    }
                    controllerSerial.connectionType == ConnectionType.SERIAL -> {
                        if (controllerSerial.sendData(message.toByteArray())) {
                            updateLogView("Sent : $message\n")
                        }
                    }
                    controllerBT.connectionType == ConnectionType.BT -> {
                        if (controllerBT.sendData(message.toByteArray())) {
                            updateLogView("Sent : $message\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLogView(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            binding.tvLog.text = "${binding.tvLog.text}$message"
        }
    }

    private fun handleUsbDevice(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice?
        }

        device?.let {
            usbDevice = it
            binding.etSerialDevice.setText(it.deviceName)
        }

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usbManager.hasPermission(device)) {
            val usbPermissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, usbPermissionIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothDevice(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
        }

        device?.let {
            bluetoothDevice = it
            binding.etBluetoothDevice.setText(it.name)
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }
}