package com.github.zeckson.yeelc

import androidx.appcompat.app.AppCompatActivity
import android.app.ProgressDialog

import android.os.Handler
import android.os.Message
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.SeekBar

import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket


class ControlActivity : AppCompatActivity() {
    private val TAG = "Control"

    private var mCmdId: Int = 0
    private var mSocket: Socket? = null
    private var mBulbIP: String? = null
    private var mBulbPort: Int = 0
    private var mProgressDialog: ProgressDialog? = null
    private var mBrightness: SeekBar? = null
    private var mCT: SeekBar? = null
    private var mColor: SeekBar? = null
    private var mBtnOn: Button? = null
    private var mBtnOff: Button? = null
    private val mBtnMusic: Button? = null
    private var mBos: BufferedOutputStream? = null
    private var mReader: BufferedReader? = null
    private val mHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MSG_CONNECT_FAILURE -> mProgressDialog!!.dismiss()
                MSG_CONNECT_SUCCESS -> mProgressDialog!!.dismiss()
            }
        }
    }

    private var cmd_run = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        mBulbIP = intent.getStringExtra("ip")
        mBulbPort = Integer.parseInt(intent.getStringExtra("port"))
        mProgressDialog = ProgressDialog(this)
        mProgressDialog!!.setMessage("Connecting...")
        mProgressDialog!!.setCancelable(false)
        mProgressDialog!!.show()
        mBrightness = findViewById<SeekBar>(R.id.brightness)
        mColor = findViewById<SeekBar>(R.id.color)
        mCT = findViewById<SeekBar>(R.id.ct)
        mCT!!.max = 4800
        mColor!!.max = 360
        mBrightness!!.max = 100

        mBrightness!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                write(parseBrightnessCmd(seekBar.progress))
            }
        })
        mCT!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                write(parseCTCmd(seekBar.progress + 1700))
            }
        })
        mColor!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                write(parseColorCmd(seekBar.progress))
            }
        })
        mBtnOn = findViewById(R.id.btn_on) as Button
        mBtnOff = findViewById(R.id.btn_off) as Button
        mBtnOn!!.setOnClickListener { write(parseSwitch(true)) }
        mBtnOff!!.setOnClickListener { write(parseSwitch(false)) }
        connect()
    }

    private fun connect() {
        Thread(Runnable {
            try {
                cmd_run = true
                mSocket = Socket(mBulbIP, mBulbPort)
                mSocket!!.keepAlive = true
                mBos = BufferedOutputStream(mSocket!!.getOutputStream())
                mHandler.sendEmptyMessage(MSG_CONNECT_SUCCESS)
                mReader = BufferedReader(InputStreamReader(mSocket!!.getInputStream()))
                while (cmd_run) {
                    try {
                        val value = mReader!!.readLine()
                        Log.d(TAG, "value = $value")
                    } catch (e: Exception) {

                    }

                }
            } catch (e: Exception) {
                e.printStackTrace()
                mHandler.sendEmptyMessage(MSG_CONNECT_FAILURE)
            }
        }).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cmd_run = false
            if (mSocket != null)
                mSocket!!.close()
        } catch (e: Exception) {

        }

    }

    private fun parseSwitch(on: Boolean): String {
        val cmd: String
        if (on) {
            cmd = CMD_ON.replace("%id", (++mCmdId).toString())
        } else {
            cmd = CMD_OFF.replace("%id", (++mCmdId).toString())
        }
        return cmd
    }

    private fun parseCTCmd(ct: Int): String {
        return CMD_CT.replace("%id", (++mCmdId).toString())
            .replace("%value", (ct + 1700).toString())
    }

    private fun parseColorCmd(color: Int): String {
        return CMD_HSV.replace("%id", (++mCmdId).toString()).replace("%value", color.toString())
    }

    private fun parseBrightnessCmd(brightness: Int): String {
        return CMD_BRIGHTNESS.replace("%id", (++mCmdId).toString())
            .replace("%value", brightness.toString())
    }

    private fun write(cmd: String) {
        if (mBos != null && mSocket!!.isConnected) {
            try {
                mBos!!.write(cmd.toByteArray())
                mBos!!.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            Log.d(TAG, "mBos = null or mSocket is closed")
        }
    }

    companion object {

        private val MSG_CONNECT_SUCCESS = 0
        private val MSG_CONNECT_FAILURE = 1
        private val CMD_TOGGLE = "{\"id\":%id,\"method\":\"toggle\",\"params\":[]}\r\n"
        private val CMD_ON =
            "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"on\",\"smooth\",500]}\r\n"
        private val CMD_OFF =
            "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"off\",\"smooth\",500]}\r\n"
        private val CMD_CT =
            "{\"id\":%id,\"method\":\"set_ct_abx\",\"params\":[%value, \"smooth\", 500]}\r\n"
        private val CMD_HSV =
            "{\"id\":%id,\"method\":\"set_hsv\",\"params\":[%value, 100, \"smooth\", 200]}\r\n"
        private val CMD_BRIGHTNESS =
            "{\"id\":%id,\"method\":\"set_bright\",\"params\":[%value, \"smooth\", 200]}\r\n"
        private val CMD_BRIGHTNESS_SCENE =
            "{\"id\":%id,\"method\":\"set_bright\",\"params\":[%value, \"smooth\", 500]}\r\n"
        private val CMD_COLOR_SCENE =
            "{\"id\":%id,\"method\":\"set_scene\",\"params\":[\"cf\",1,0,\"100,1,%color,1\"]}\r\n"
    }

}
