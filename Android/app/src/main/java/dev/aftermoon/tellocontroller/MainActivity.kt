package dev.aftermoon.tellocontroller

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.gun0912.tedpermission.coroutine.TedPermission
import dev.aftermoon.tellocontroller.databinding.ActivityMainBinding
import dev.aftermoon.tellocontroller.network.APICall
import dev.aftermoon.tellocontroller.network.BaseResponse
import dev.aftermoon.tellocontroller.network.RetrofitClient
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
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
    private var isUpDownMode = false
    private var isCaptureMode = false
    private var isAlreadySendStableStop = false

    /** Loading **/
    private var loadingDialog: LoadingDialog? = null

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
        // LoadingDialog
        loadingDialog = LoadingDialog(this)

        // Get Retrofit Client
        retrofitClient = RetrofitClient.getRetrofitClient("http://192.168.0.2:8921")
        if (retrofitClient == null) {
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
                        System.arraycopy(
                            event.values,
                            0,
                            accelerometerReading,
                            0,
                            accelerometerReading.size
                        )
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(
                            event.values,
                            0,
                            magnetometerReading,
                            0,
                            magnetometerReading.size
                        )
                    }

                    val isSuccess = SensorManager.getRotationMatrix(
                        rotationMatrix,
                        null,
                        accelerometerReading,
                        magnetometerReading
                    )
                    if (isSuccess && !isCaptureMode) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)

                        var azi = Math.toDegrees(orientationAngles[0].toDouble()) // -Z

                        // Azimuth를 0 - 360 범위로 변경
                        if (azi <= 0) {
                            azi = (180 + (180 - (azi * -1)))
                        }

                        val x = Math.toDegrees(orientationAngles[1].toDouble()) // X
                        val y = Math.toDegrees(orientationAngles[2].toDouble()) // Y
                        val etDisText =
                            viewBinding.etDistance.text.toString() // 사용자가 지정한 이동 거리 EditText

                        // 날고있으면서 마지막 명령 후 일정 시간이 지났으면
                        if (isFlying && System.currentTimeMillis() > lastSendTime + 800) {
                            // 이동거리 설정이 되어있다면
                            if (!etDisText.isEmpty()) {
                                // Azimuth는 절대적 수치를 나타냄
                                // 시작 Azimuth를 저장하고 이를 이용해서 상대적 수치를 계산함
                                if (startAzi == Double.MIN_VALUE) {
                                    startAzi = azi
                                }

                                val moveDistance = etDisText.toInt()
                                // 이동거리가 20 ~ 500 사이일 경우
                                if (moveDistance in 20..500) {
                                    // 일반 전진 모드일 경우
                                    if (!isUpDownMode) {
                                        // 앞
                                        if (x >= 28) {
                                            move(0, moveDistance)
                                        }
                                        // 뒤
                                        else if (x < -50) {
                                            move(1, moveDistance)
                                        }
                                        // 왼쪽
                                        else if (y <= -48) {
                                            move(2, moveDistance)
                                        }
                                        // 오른쪽
                                        else if (y >= 48) {
                                            move(3, moveDistance)
                                        }
                                        // 한 번도 돈 적이 없거나 마지막으로 회전하고 일정 시간이 지났으면
                                        else if (lastRotateTime < 0 || System.currentTimeMillis() > lastRotateTime + 2000) {
                                            // 시작 각도와 차이 구함
                                            var realSubAngle = startAzi - azi
                                            var subAngle = abs(realSubAngle)

                                            // 360 - 회전할 Angle 값이 회전한 Angle보다 작다면 반대가 더 효율적이므로
                                            if (abs(360 - subAngle) < subAngle) {
                                                // -1로 반대로 돌 수 있도록 해줌
                                                realSubAngle *= -1

                                                // 실제 도는 각도 변경
                                                subAngle = abs(360 - subAngle)
                                            }

                                            //30 ~ 330도 이상의 변화일 경우
                                            if (subAngle in 30.0..330.0) {
                                                Log.d(
                                                    "CalAngle",
                                                    "realSubAngle $realSubAngle subAngle $subAngle"
                                                )

                                                // 값이 -인지 +인지에 따라 방향 결정
                                                if (realSubAngle < 0) {
                                                    // CW (오른쪽)
                                                    rotate(0, subAngle.toInt() + 15)
                                                } else {
                                                    // CCW (왼쪽)
                                                    rotate(1, subAngle.toInt() + 15)
                                                }
                                                startAzi = azi // 새 각도로 변했으므로 새 각도를 startAzi로
                                                lastRotateTime = System.currentTimeMillis()
                                            }
                                        } else if (!isAlreadySendStableStop) {
                                            // 그냥 정지 보내기
                                            changeFlyingState(20)
                                            isAlreadySendStableStop = true
                                        }
                                    } else {
                                        // 앞으로 기울이면 위로
                                        if (x >= 28) {
                                            move(4, moveDistance)
                                        }
                                        // 뒤로 기울이면 아래로
                                        else if (x < -50) {
                                            move(5, moveDistance)
                                        }

                                    }
                                }
                            }
                            lastSendTime = System.currentTimeMillis()
                        }

                        // TextView에 Azimuth, Pitch, Roll 표시
                        viewBinding.tvAzimuth.text = getString(
                            R.string.angle,
                            "Azimuth (-z)",
                            (round(azi * 100) / 100).toString()
                        )
                        viewBinding.tvXpos.text = getString(
                            R.string.angle,
                            "Pitch (x)",
                            (round(x * 100) / 100).toString()
                        )
                        viewBinding.tvYpos.text =
                            getString(R.string.angle, "Roll (y)", (round(y * 100) / 100).toString())
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor != null) Log.d(
                    "MainActivity",
                    "Accuracy Changed ${sensor.name} / $accuracy"
                )
            }
        }
    }

    /**
     * 버튼 이벤트 설정
     */
    private fun setButtonEvent() {
        // 이/착륙 버튼
        viewBinding.btnTakeoff.setOnClickListener {
            loadingDialog!!.show()
            // Reset Value
            startAzi = Double.MIN_VALUE
            lastSendTime = 0L
            lastRotateTime = Long.MIN_VALUE

            if (!isFlying) {
                if (viewBinding.etDistance.text.isNullOrEmpty()) {
                    viewBinding.etDistance.setText("30")
                }

                if (viewBinding.etSpeed.text.isNullOrEmpty()) {
                    viewBinding.etSpeed.setText("30")
                }

                viewBinding.btnTakeoff.text = getString(R.string.btn_land)
                viewBinding.btnEmergency.visibility = View.VISIBLE
                viewBinding.btnForcestop.visibility = View.VISIBLE
                viewBinding.btnStopmode.visibility = View.VISIBLE
                viewBinding.btnMode.visibility = View.VISIBLE

                changeFlyingState(0)
                isFlying = true
            } else {
                viewBinding.btnTakeoff.text = getString(R.string.btn_takeoff)
                viewBinding.btnEmergency.visibility = View.GONE
                viewBinding.btnEmergency.visibility = View.GONE
                viewBinding.btnForcestop.visibility = View.GONE
                viewBinding.btnStopmode.visibility = View.GONE
                viewBinding.btnMode.visibility = View.GONE

                changeFlyingState(1)
                isFlying = false
                isCaptureMode = false;
                isUpDownMode = false
            }


            Handler(Looper.getMainLooper()).postDelayed({
                loadingDialog!!.dismiss()
            }, 3000)
        }

        // 비상 버튼
        viewBinding.btnEmergency.setOnClickListener {
            // Reset Value
            startAzi = Double.MIN_VALUE
            lastSendTime = 0L
            lastRotateTime = Long.MIN_VALUE

            viewBinding.btnTakeoff.text = getString(R.string.btn_takeoff)
            viewBinding.btnEmergency.visibility = View.GONE
            viewBinding.btnForcestop.visibility = View.GONE
            viewBinding.btnStopmode.visibility = View.GONE
            viewBinding.btnMode.visibility = View.GONE

            changeFlyingState(3)
            isFlying = false
            isCaptureMode = false;
            isUpDownMode = false
        }

        // 모드 변경 버튼
        viewBinding.btnMode.setOnClickListener {
            if (isUpDownMode) {
                viewBinding.btnMode.text = getString(R.string.btn_up)
                isUpDownMode = false
            } else {
                viewBinding.btnMode.text = getString(R.string.btn_move)
                isUpDownMode = true
            }
        }

        // 사진 촬영 모드 버튼
        viewBinding.btnStopmode.setOnClickListener {
            loadingDialog!!.show()

            if (isCaptureMode) {
                changeFlyingState(6)
                isCaptureMode = false
                viewBinding.btnStopmode.text = getString(R.string.btn_stop)
                viewBinding.btnCapture.visibility = View.GONE
                viewBinding.btnForcestop.visibility = View.VISIBLE
                viewBinding.btnMode.isEnabled = true
            } else {
                startAzi = Double.MIN_VALUE
                isCaptureMode = true
                changeFlyingState(21) // 바로 정지
                changeFlyingState(5)
                viewBinding.btnStopmode.text = getString(R.string.btn_stop_unlock)
                viewBinding.btnCapture.visibility = View.VISIBLE
                viewBinding.btnForcestop.visibility = View.GONE
                viewBinding.btnMode.isEnabled = false // 모드 변경 불가능하게
            }

            Handler(Looper.getMainLooper()).postDelayed({
                loadingDialog!!.dismiss()
            }, 2000)
        }

        // 촬영 버튼
        viewBinding.btnCapture.setOnClickListener {
            if (isCaptureMode) {
                capture()
            }
        }

        // 속도 저장
        viewBinding.btnSpeedsave.setOnClickListener {
            if (isFlying && viewBinding.etSpeed.text.toString().isNotEmpty()) {
                val value = viewBinding.etSpeed.text.toString().toInt()
                if (value in 10..100) setSpeed(value)
            }
        }

        // 강제 정지 버튼
        viewBinding.btnForcestop.setOnClickListener {
            if (isFlying) {
                isAlreadySendStableStop = false
                changeFlyingState(21)
            }
        }
    }

    /**
     * 비행 상태 변화 신호 전송
     */
    private fun changeFlyingState(type: Int) {
        Log.d("changeFlyingState", "Type $type")

        var callResponse: Call<BaseResponse>? = null

        if (type == 0) {
            callResponse = apiCall!!.takeOff()
        } else if (type == 1) {
            callResponse = apiCall!!.land()
        } else if (type == 20) {
            callResponse = apiCall!!.stop(0)
        } else if (type == 21) {
            callResponse = apiCall!!.stop(1)
        } else if (type == 3) {
            callResponse = apiCall!!.emergency()
        } else if (type == 4) {
            callResponse = apiCall!!.capture()
        } else if (type == 5) {
            callResponse = apiCall!!.streamon()
        } else if (type == 6) {
            callResponse = apiCall!!.streamoff()
        }


        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(call: Call<BaseResponse>, response: Response<BaseResponse>) {
                val body = response.body()
                if (body != null) {
                    if (body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                } else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    /**
     * 이동 신호 전송
     */
    private fun move(type: Int, distance: Int) {
        Log.d("Move", "Type $type / Distance $distance cm")
        var callResponse: Call<BaseResponse>? = null

        if (type == 0) {
            callResponse = apiCall!!.forward(distance)
        } else if (type == 1) {
            callResponse = apiCall!!.back(distance)
        } else if (type == 2) {
            callResponse = apiCall!!.left(distance)
        } else if (type == 3) {
            callResponse = apiCall!!.right(distance)
        } else if (type == 4) {
            callResponse = apiCall!!.up(distance)
        } else if (type == 5) {
            callResponse = apiCall!!.down(distance)
        }

        isAlreadySendStableStop = false

        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(call: Call<BaseResponse>, response: Response<BaseResponse>) {
                val body = response.body()
                if (body != null) {
                    if (body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                } else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    /**
     * 캡처하기
     */
    private fun capture() {
        val callResponse: Call<BaseResponse> = apiCall!!.capture()

        callResponse.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(call: Call<BaseResponse>, response: Response<BaseResponse>) {
                val body = response.body()
                if (body != null) {
                    if (body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.error),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // 성공했으면 사진 저장 시간 고려해서 잠시 Delayed
                        Handler(Looper.getMainLooper()).postDelayed({
                            getCapture()
                        }, 8000)
                    }
                } else {
                    Log.i("MainActivity", "Body NULL")
                    Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    /**
     * 캡쳐 사진 얻어오기
     */
    private fun getCapture() {
        val callResponse: Call<ResponseBody> = apiCall!!.getcapture()
        val dateFormat = SimpleDateFormat("yyyymmddHHmmss", Locale.getDefault())
        val date = dateFormat.format(Calendar.getInstance().time)

        callResponse.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val bodyStream = response.body()!!.byteStream()
                        val imageBA = bodyStream.readBytes()
                        val bitmap: Bitmap? =
                            BitmapFactory.decodeByteArray(imageBA, 0, imageBA.size)

                        if (bitmap != null) {
                            val bos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos)

                            val collection =
                                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "droneimage-${date}")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                put(MediaStore.Images.Media.IS_PENDING, 1)
                            }

                            val item = contentResolver.insert(collection, values)!!

                            contentResolver.openFileDescriptor(item, "w", null).use {
                                FileOutputStream(it!!.fileDescriptor).use { outputStream ->
                                    val imageInputStream = ByteArrayInputStream(bos.toByteArray())

                                    while (true) {

                                        val data = imageInputStream.read()
                                        if (data == -1) {
                                            break
                                        }
                                        outputStream.write(data)
                                    }
                                    imageInputStream.close()
                                    outputStream.close()
                                }
                            }

                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(item, values, null, null)

                            CoroutineScope(Main).launch {
                                Toast.makeText(this@MainActivity, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            CoroutineScope(Main).launch {
                                Log.e("MainActivity", "Error! ${response.errorBody()}")
                                Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                else {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error),
                    Toast.LENGTH_SHORT
                ).show()
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
        } else if (type == 1) {
            callResponse = apiCall!!.rotate_ccw(angle)
        }

        isAlreadySendStableStop = false

        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(
                call: Call<BaseResponse>,
                response: Response<BaseResponse>
            ) {
                val body = response.body()
                if (body != null) {
                    if (body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                } else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        })
    }

    /**
     * 속도 설정
     */
    private fun setSpeed(speed: Int) {
        var callResponse: Call<BaseResponse> = apiCall!!.speed(speed)

        callResponse!!.enqueue(object : Callback<BaseResponse> {
            override fun onResponse(
                call: Call<BaseResponse>,
                response: Response<BaseResponse>
            ) {
                val body = response.body()
                if (body != null) {
                    if (body.code != 200) {
                        Log.i("MainActivity", "${body.code} ${body.message}")
                    }
                } else {
                    Log.i("MainActivity", "Body NULL")
                }
            }

            override fun onFailure(call: Call<BaseResponse>, t: Throwable) {
                Log.e("MainActivity", "Error!", t)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error),
                    Toast.LENGTH_SHORT
                )
                    .show()
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

        if (!permission.isGranted) {
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        // Sensor Listener 등록
        sensorManager!!.registerListener(
            sensorEventListener,
            accelSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager!!.registerListener(
            sensorEventListener,
            magneticSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onPause() {
        super.onPause()
        if (sensorManager != null && sensorEventListener != null) {
            // Sensor Listener 등록 해제
            sensorManager!!.unregisterListener(sensorEventListener)
        }
    }

}