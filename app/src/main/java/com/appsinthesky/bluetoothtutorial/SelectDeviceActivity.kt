package com.appsinthesky.bluetoothtutorial

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.select_device_layout.*
import org.jetbrains.anko.toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


internal class WebWorker(private val callback: (res: String) -> Unit) : AsyncTask<Any?, Void?, String>() {
    enum class RequestType {
        POST, GET, PUT, DELETE
    }

    private val exception: Exception? = null

    public override fun doInBackground(vararg params: Any?): String? {
        if(!(params[0] is RequestType && params[1] is String && params[2] is Map<*, *>))
            return "Error, params is not valid";

        var reqParam = "";
        for ((k, v) in params[2] as Map<*, *>) {
            reqParam +="${if(reqParam.isEmpty()) "" else "&"}${URLEncoder.encode(k as String, "UTF-8")}=${URLEncoder.encode(v as String, "UTF-8")}"
        }
        // Трайкачить
        val mURL = URL(params[1] as String)

        with(mURL.openConnection() as HttpURLConnection) {
            // optional default is GET
            requestMethod = "POST"

            val wr = OutputStreamWriter(getOutputStream());
            wr.write(reqParam);
            wr.flush();

            println("URL : $url")
            println("Response Code : $responseCode")

            BufferedReader(InputStreamReader(inputStream)).use {
                val response = StringBuffer()

                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                println("Response : $response")
                return response.toString()
            }
        }
    }

    override fun onPostExecute(response: String) {
        // TODO: check this.exception
        // TODO: do something with the feed\
        // private val context: Context
        callback(response)
    }
}

class SelectDeviceActivity : AppCompatActivity() {

    private var m_bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var m_pairedDevices: Set<BluetoothDevice>
    private val REQUEST_ENABLE_BLUETOOTH = 1

    companion object {
        val EXTRA_ADDRESS: String = "Device_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_device_layout)

        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (m_bluetoothAdapter == null) {
            toast("this device doesn't support bluetooth")
            return
        }
        if (!m_bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }

        select_device_refresh.setOnClickListener { pairedDeviceList() }

        // POST Handlerer
        getActiveConnections.setOnClickListener {
            // Создаем объект и передаем в него коллбек
            WebWorker { result ->
                // Запускаем в UI потоке, потому что надо, вот почему
                (runOnUiThread(Runnable {
                    // Делаем то, что нам было необходимо с UI потоком, аргумент делегата - result
                    Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show()
                }))
            }.execute(
                    WebWorker.RequestType.POST,
                    "http://deliveryhugs.ru",
                    mapOf("method" to "getApplicationData", "id" to "kotlinApp")) // Передаем в воркер параметры
        }
    }

    private fun pairedDeviceList() {
        m_pairedDevices = m_bluetoothAdapter!!.bondedDevices
        val list: ArrayList<BluetoothDevice> = ArrayList()

        if (!m_pairedDevices.isEmpty()) {
            for (device: BluetoothDevice in m_pairedDevices) {
                list.add(device)
                Log.i("device", "" + device)
            }
        } else {
            toast("no paired bluetooth devices found")
        }

        val DeviceNames = ArrayList<String>()
        for (device: BluetoothDevice in list) {
            DeviceNames.add(device.name)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, DeviceNames)
        select_device_list.adapter = adapter
        select_device_list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val device: BluetoothDevice = list[position]
            val address: String = device.address

            val intent = Intent(this, ControlActivity::class.java)
            intent.putExtra(EXTRA_ADDRESS, address)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (m_bluetoothAdapter!!.isEnabled) {
                    toast("Bluetooth has been enabled")
                } else {
                    toast("Bluetooth has been disabled")
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                toast("Bluetooth enabling has been canceled")
            }
        }
    }
}
