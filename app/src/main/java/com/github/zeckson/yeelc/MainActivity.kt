package com.github.zeckson.yeelc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.github.zeckson.yeelc.ui.main.MainFragment
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket


class MainActivity : AppCompatActivity() {
    private val TAG = "APITEST"
    private val MSG_SHOWLOG = 0
    private val MSG_FOUND_DEVICE = 1
    private val MSG_DISCOVER_FINISH = 2
    private val MSG_STOP_SEARCH = 3

    private val UDP_HOST = "239.255.255.250"
    private val UDP_PORT = 1982
    private val message = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST:239.255.255.250:1982\r\n" +
            "MAN:\"ssdp:discover\"\r\n" +
            "ST:wifi_bulb\r\n"//Datagram
    private var mDSocket: DatagramSocket? = null
    private var mSeraching = true
    private var mListView: ListView? = null
    private var mAdapter: MyAdapter? = null
    var mDeviceList: ArrayList<HashMap<String, String>> =
        ArrayList()
    private val textView: TextView? = null
    private val btnSearch: Button? = null
    private val mHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MSG_FOUND_DEVICE -> mAdapter!!.notifyDataSetChanged()
                MSG_SHOWLOG -> Toast.makeText(
                    this@MainActivity,
                    "" + msg.obj.toString(),
                    Toast.LENGTH_SHORT
                ).show()
                MSG_STOP_SEARCH -> {
                    mSearchThread!!.interrupt()
                    mAdapter!!.notifyDataSetChanged()
                    mSeraching = false
                }
                MSG_DISCOVER_FINISH -> mAdapter!!.notifyDataSetChanged()
            }
        }
    }

    private var multicastLock: MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance())
                .commitNow()
        }
        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("test")
        lock.run {
            acquire()

        }
        multicastLock = lock;
        val textView = findViewById<TextView>(R.id.main_text)
        val btnSearch = findViewById<Button>(R.id.search_button)
        btnSearch.setOnClickListener { searchDevice() }
        val listView = findViewById(R.id.deviceList) as ListView
        mListView = listView
        mAdapter = MyAdapter(this)
        listView.setAdapter(mAdapter)
        listView.setOnItemClickListener { parent, view, position, id ->
            val bulbInfo = mDeviceList.get(position)
            val intent = Intent(this@MainActivity, ControlActivity::class.java)
            val ipinfo = bulbInfo.get("Location")!!.split("//")[1]
            val ip =
                ipinfo.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            val port =
                ipinfo.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            intent.putExtra("bulbinfo", bulbInfo)
            intent.putExtra("ip", ip)
            intent.putExtra("port", port)
            startActivity(intent)
        }
    }


    private var mSearchThread: Thread? = null
    private fun searchDevice() {

        mDeviceList.clear()
        mAdapter!!.notifyDataSetChanged()
        mSeraching = true
        mSearchThread = Thread(Runnable {
            try {
                mDSocket = DatagramSocket()
                val dpSend = DatagramPacket(
                    message.toByteArray(),
                    message.toByteArray().size, InetAddress.getByName(UDP_HOST),
                    UDP_PORT
                )
                mDSocket!!.send(dpSend)
                mHandler.sendEmptyMessageDelayed(MSG_STOP_SEARCH, 2000)
                while (mSeraching) {
                    val buf = ByteArray(1024)
                    val dpRecv = DatagramPacket(buf, buf.size)
                    mDSocket!!.receive(dpRecv)
                    val bytes = dpRecv.getData()
                    val buffer = StringBuffer()
                    for (i in 0 until dpRecv.getLength()) {
                        // parse /r
                        if (bytes[i].toInt() == 13) {
                            continue
                        }
                        buffer.append(bytes[i].toChar())
                    }
                    Log.d("socket", "got message:$buffer")
                    if (!buffer.toString().contains("yeelight")) {
                        mHandler.obtainMessage(MSG_SHOWLOG, "Found not Light Device").sendToTarget()
                        return@Runnable
                    }
                    val infos =
                        buffer.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    val bulbInfo = HashMap<String, String>()
                    for (str in infos) {
                        val index = str.indexOf(":")
                        if (index == -1) {
                            continue
                        }
                        val title = str.substring(0, index)
                        val value = str.substring(index + 1)
                        bulbInfo[title] = value
                    }
                    if (!hasAdd(bulbInfo)) {
                        mDeviceList.add(bulbInfo)
                    }

                }
                mHandler.sendEmptyMessage(MSG_DISCOVER_FINISH)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
        mSearchThread!!.start()

    }

    private var mNotify = true
    override fun onResume() {
        super.onResume()
        Thread(Runnable {
            try {
                //DatagramSocket socket = new DatagramSocket(UDP_PORT);
                val group = InetAddress.getByName(UDP_HOST)
                val socket = MulticastSocket(UDP_PORT)
                socket.setLoopbackMode(true)
                socket.joinGroup(group)
                Log.d(TAG, "join success")
                mNotify = true
                while (mNotify) {
                    val buf = ByteArray(1024)
                    val receiveDp = DatagramPacket(buf, buf.size)
                    Log.d(TAG, "waiting device....")
                    socket.receive(receiveDp)
                    val bytes = receiveDp.data
                    val buffer = StringBuffer()
                    for (i in 0 until receiveDp.length) {
                        // parse /r
                        if (bytes[i].toInt() == 13) {
                            continue
                        }
                        buffer.append(bytes[i].toChar())
                    }
                    if (!buffer.toString().contains("yeelight")) {
                        Log.d(TAG, "Listener receive msg:$buffer but not a response")
                        return@Runnable
                    }
                    val infos =
                        buffer.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    val bulbInfo = HashMap<String, String>()
                    for (str in infos) {
                        val index = str.indexOf(":")
                        if (index == -1) {
                            continue
                        }
                        val title = str.substring(0, index)
                        val value = str.substring(index + 1)
                        Log.d(TAG, "title = $title value = $value")
                        bulbInfo[title] = value
                    }
                    if (!hasAdd(bulbInfo)) {
                        mDeviceList.add(bulbInfo)
                    }
                    mHandler.sendEmptyMessage(MSG_FOUND_DEVICE)
                    Log.d(TAG, "get message:$buffer")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }).start()
    }

    override fun onPause() {
        super.onPause()
        mNotify = false
    }

    override fun onDestroy() {
        super.onDestroy()
        multicastLock?.release()
    }

    private inner class MyAdapter(context: Context) : BaseAdapter() {

        private val mLayoutInflater: LayoutInflater
        private val mLayoutResource: Int

        init {
            mLayoutInflater = LayoutInflater.from(context)
            mLayoutResource = android.R.layout.simple_list_item_2
        }

        override fun getCount(): Int {
            return mDeviceList.size
        }

        override fun getItem(position: Int): Any {
            return mDeviceList[position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val data = getItem(position) as HashMap<*, *>
            if (convertView == null) {
                view = mLayoutInflater.inflate(mLayoutResource, parent, false)
            } else {
                view = convertView
            }
            val textView = view.findViewById<View>(android.R.id.text1) as TextView
            textView.text = "Type = " + data["model"]

            Log.d(TAG, "name = " + textView.text.toString())
            val textSub = view.findViewById<View>(android.R.id.text2) as TextView
            textSub.text = "location = " + data["Location"]
            return view
        }
    }

    private fun hasAdd(bulbinfo: HashMap<String, String>): Boolean {
        for (info in mDeviceList) {
            Log.d(TAG, "location params = " + bulbinfo["Location"])
            if (info["Location"].equals(bulbinfo["Location"])) {
                return true
            }
        }
        return false
    }

}
