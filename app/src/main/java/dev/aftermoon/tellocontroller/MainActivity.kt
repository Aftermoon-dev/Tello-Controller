package dev.aftermoon.tellocontroller

import android.Manifest
import android.content.Context
import android.hardware.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.gun0912.tedpermission.coroutine.TedPermission
import dev.aftermoon.tellocontroller.databinding.ActivityMainBinding
import dev.aftermoon.tellocontroller.network.APICall
import dev.aftermoon.tellocontroller.network.BaseResponse
import dev.aftermoon.tellocontroller.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.sql.Timestamp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round

class MainActivity : AppCompatActivity() {
    /** Android ViewBinding **/
    private lateinit var viewBinding: ActivityMainBinding

    /** 센서 관련 변수 **/
    private var sensorManager: SensorManager? = null
    private lateinit var accelSensor: Sensor
    private lateinit var magneticSensor: Sensor
    private var sensorEventListener: SensorEventListener? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /** API 호출 관련 변수 **/
    private var retrofitClient: Retrofit? = null
    private var apiCall: APICall? = null

    /** 상태 관련 변수 **/
    private var isFlying = false
    private var beforeX: Double = 0.0
    private var beforeY: Double = 0.0
    private var beforeAzi: Double = 0.0
    private var lastSendTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // 스크린 ON 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initVariable()
        setButtonEvent()

        CoroutineScope(Main).launch {
            // 퍼미션 확인
            checkPermission()
        }
    }

    /**
     * 초기 변수 설정
     */
    private fun initVariable() {
        // Get Retrofit Client
        retrofitClient = RetrofitClient.getRetrofitClient("http://192.168.0.2:8921")
        if(retrofitClient == null) {
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            finish()
        }

        apiCall = retrofitClient!!.create(APICall::class.java)

        // Sensor 변수
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Sensor Event Listener
        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    }
                    else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    }

                    val isSuccess = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
                    if (isSuccess) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        // 왼쪽으로 기울이면 Y -55 이하
                        // 오른쪽으로 기울이면 Y 45 이상
                        // 앞으로 기울이면 X 25 이상
                        // 뒤로 기울이면 X -35 이하
                        // 왼쪽으로 틀면 Z가 -44 -> -130

                        val azi = Math.toDegrees(orientationAngles[0].toDouble())
                        val x = Math.toDegrees(orientationAngles[1].toDouble())
                        val y = Math.toDegrees(orientationAngles[2].toDouble())

                        // 날고 있으면서 마지막 명령 후 0.2초가 지났다면
                        if (isFlying && System.currentTimeMillis() > lastSendTime + 200) {
                            // 앞
                            if (x >= 25) {
                                Log.d("move", "${abs((25 - x)).toInt()}")
                                move(0, abs((25 - x)).toInt())
                            }
                            // 뒤
                            else if (x <= -35) {
                                Log.d("move", "${abs((-35 - x)).toInt()}")
                                move(1, abs((-35 - x)).toInt())
                            }
                            // 왼쪽
                            else if (y <= -55) {
                                Log.d("move", "${abs((-55 - y)).toInt()}")
                                move(2, abs((-55 - y)).toInt())
                            }
                            // 오른쪽
                            else if (y >= 45) {
                                Log.d("move", "${abs((45 - y)).toInt()}")
                                move(3, abs((45 - y)).toInt())
                            }
                            lastSendTime = System.currentTimeMillis()
                        }

                        viewBinding.tvAzimuth.text = getString(R.string.angle, "Azimuth", (round(azi*100)/100).toString())
                        viewBinding.tvXpos.text = getString(R.string.angle, "X", (round(x*100)/100).toString())
                        viewBinding.tvYpos.text = getString(R.string.angle, "Y", (round(y*100)/100).toString())

                        beforeAzi = azi
                        beforeX = x
                        beforeY = y
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor != null) Log.d("MainActivity", "Accuracy Changed ${sensor.name} / $accuracy")
            }
        }
    }

    /**
     * 버튼 이벤트 설정
     */
    private fun setButtonEvent() {
        // 이/착륙 버튼
        viewBinding.btnTakeoff.setOnClickListener {
            if (!isFlying) {
                viewBinding.btnTakeoff.text = getString(R.string.btn_land)
                viewBinding.btnEmergency.visibility = View.VISIBLE
                changeFlyingState(0)
                isFlying = true
            }
            else {
                viewBinding.btnTakeoff.text = getString(R.string.btn_takeoff)
                viewBinding.btnEmergency.visibility = View.GONE
                changeFlyingState(1)
                isFlying = false
            }
        }

        // 비상 버튼
        viewBinding.btnEmergency.setOnClickListener {
            viewBinding.btnTakeoff.text = getString(R.string.btn_takeoff)
            viewBinding.btnEmergency.visibility = View.GONE
            changeFlyingState(2)
            isFlying = false
        }
    }

    /**
     * 이륙/착륙 신호 전송
     */
    private fun changeFlyingState(type: Int) {
        var callResponse: Call<BaseResponse>? = null

        if (type == 0) {
            callResponse = apiCall!!.takeOff()
        }
        else if (type == 1) {
            callResponse = apiCall!!.land()
        }
        else if (type == 2) {
            callResponse = apiCall!!.emergency()
        }

        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(call: Call<BaseResponse>, response: Response<BaseResponse>) {
                val body = response.body()
                if(body != null) {
                    if(body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                }
                else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * 전진 신호 전송
     */
    private fun move(type: Int, distance: Int) {
        var callResponse: Call<BaseResponse>? = null

        if (type == 0) {
            callResponse = apiCall!!.forward(distance)
        }
        else if (type == 1) {
            callResponse = apiCall!!.back(distance)
        }
        else if (type == 2) {
            callResponse = apiCall!!.left(distance)
        }
        else if (type == 3) {
            callResponse = apiCall!!.right(distance)
        }

        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(call: Call<BaseResponse>, response: Response<BaseResponse>) {
                val body = response.body()
                if(body != null) {
                    if(body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                }
                else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * 회전 신호 전송
     */
    private fun rotate(type: Int, angle: Int) {
        var callResponse: Call<BaseResponse>? = null

        if (type == 0) {
            callResponse = apiCall!!.rotate_cw(angle)
        }
        else if (type == 1) {
            callResponse = apiCall!!.rotate_ccw(angle)
        }

        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(call: Call<BaseResponse>, response: Response<BaseResponse>) {
                val body = response.body()
                if(body != null) {
                    if(body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                }
                else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * 필요한 Permission 확인
     */
    suspend fun checkPermission() {
        val permission = TedPermission.create()
            .setPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
            .check()

        if(!permission.isGranted) {
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        // Sensor Listener 등록
        sensorManager!!.registerListener(sensorEventListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager!!.registerListener(sensorEventListener, magneticSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        if (sensorManager != null && sensorEventListener != null) {
            // Sensor Listener 등록 해제
            sensorManager!!.unregisterListener(sensorEventListener)
        }
    }

}