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
    private var startAzi: Double = Double.MIN_VALUE
    private var lastSendTime: Long = 0L
    private var lastRotateTime: Long = Long.MIN_VALUE

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

                        var azi = Math.toDegrees(orientationAngles[0].toDouble()) // -Z

                        // Azimuth를 0 - 360 범위로 변경
                        if (azi <= 0) {
                            azi = (180 + (180 - (azi * -1)))
                        }

                        val x = Math.toDegrees(orientationAngles[1].toDouble()) // X
                        val y = Math.toDegrees(orientationAngles[2].toDouble()) // Y
                        val etDisText = viewBinding.etDistance.text.toString() // 사용자가 지정한 이동 거리 EditText

                        // 날고있으면서 마지막 명령 후 일정 시간이 지났으면
                        if(isFlying && System.currentTimeMillis() > lastSendTime + 430) {
                            // Azimuth는 절대적 수치를 나타냄
                            // 시작 Azimuth를 저장하고 이를 이용해서 상대적 수치를 계산함
                            if(startAzi == Double.MIN_VALUE) {
                                startAzi = azi
                            }

                            // 한 번도 돈 적이 없거나 마지막으로 회전하고 일정 시간이 지났으면
                            if (lastRotateTime < 0 || System.currentTimeMillis() > lastRotateTime + 3000) {
                                // 시작 각도와 차이 구함
                                var realSubAngle = startAzi - azi
                                var subAngle = abs(realSubAngle)

                                // 돌 Angle - 180 값이 180보다 크다면 반대로 도는게 더 효율적이므로 방향 변경
                                if (abs(360 - subAngle) < subAngle) {
                                    realSubAngle *= -1
                                    subAngle = abs(360 - subAngle)
                                }

                                //30 ~ 180도 이상의 변화일 경우
                                if (subAngle in 30.0..330.0) {
                                    Log.d("CalAngle", "realSubAngle $realSubAngle subAngle $subAngle")

                                    // 값이 -인지 +인지에 따라 방향 결정
                                    if (realSubAngle < 0) {
                                        // CW (오른쪽)
                                        rotate(0, subAngle.toInt())
                                    }
                                    else {
                                        // CCW (왼쪽)
                                        rotate(1, subAngle.toInt())
                                    }
                                    startAzi = azi // 새 각도로 변했으므로 새 각도를 startAzi로
                                    lastRotateTime = System.currentTimeMillis()
                                    lastSendTime = System.currentTimeMillis()
                                }
                            }
                            else {
                                // 이동거리 설정이 되어있다면
                                if (!etDisText.isEmpty()) {
                                    val moveDistance = etDisText.toInt()

                                    // 이동거리가 20 ~ 500 사이일 경우
                                    if (moveDistance in 20..500) {
                                        if (x >= 40) {
                                            move(0, moveDistance)
                                        }
                                        // 뒤
                                        else if (x < -55) {
                                            move(1, moveDistance)
                                        }
                                        // 왼쪽
                                        else if (y <= -55) {
                                            move(2, moveDistance)
                                        }
                                        // 오른쪽
                                        else if (y >= 55) {
                                            move(3, moveDistance)
                                        }
                                    }
                                }
                                lastSendTime = System.currentTimeMillis()
                            }
                        }

                        // TextView에 Azimuth, Pitch, Roll 표시
                        viewBinding.tvAzimuth.text = getString(R.string.angle, "Azimuth (-z)", (round(azi*100)/100).toString())
                        viewBinding.tvXpos.text = getString(R.string.angle, "Pitch (x)", (round(x*100)/100).toString())
                        viewBinding.tvYpos.text = getString(R.string.angle, "Roll (y)", (round(y*100)/100).toString())
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
            // Reset Value
            startAzi = Double.MIN_VALUE
            lastSendTime = 0L
            lastRotateTime = Long.MIN_VALUE

            if (!isFlying) {
                if (viewBinding.etDistance.text.isNullOrEmpty()) {
                    viewBinding.etDistance.setText("20")
                }

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
            // Reset Value
            startAzi = Double.MIN_VALUE
            lastSendTime = 0L
            lastRotateTime = Long.MIN_VALUE

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
        Log.d("Move", "Type $type / Distance $distance cm")
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
        Log.d("Rotate", "Type $type / Angle $angle")
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
        sensorManager!!.registerListener(sensorEventListener, magneticSensor,  SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        if (sensorManager != null && sensorEventListener != null) {
            // Sensor Listener 등록 해제
            sensorManager!!.unregisterListener(sensorEventListener)
        }
    }

}