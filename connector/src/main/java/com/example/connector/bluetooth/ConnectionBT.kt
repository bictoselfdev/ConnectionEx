package com.example.connector.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.connector.common.ConnectionHandler
import com.example.connector.common.ConnectionUtil
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ConnectionBT(private val bluetoothDevice: BluetoothDevice) : ConnectionHandler {
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")) // SPP
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()  // 연결 전 디스커버리 중지

            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream

            Log.d("Connector(BT)", "Connected to ${bluetoothDevice.name}")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()  // 연결 실패 시 소켓 닫기
            false
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            inputStream = null
            outputStream = null
            bluetoothSocket = null

            Log.d("Connector(BT)", "Disconnected from Bluetooth")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(data)
            outputStream?.flush()

            Log.d("Connector(BT)", "Data sent: ${String(data).trim()}")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun receiveData(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(ConnectionUtil.BUFFER_SIZE)
            val bytesRead = inputStream?.read(buffer) ?: -1
            if (bytesRead > 0) {
                val receivedData = buffer.copyOf(bytesRead)

                Log.d("Connector(BT)", "Data received: ${String(receivedData).trim()}")
                return@withContext receivedData
            }
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}