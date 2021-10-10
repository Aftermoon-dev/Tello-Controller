package dev.aftermoon.tellocontroller

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.gun0912.tedpermission.coroutine.TedPermission
import dev.aftermoon.tellocontroller.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        CoroutineScope(Main).launch {
            checkPermission()
        }
    }

    /**
     * 필요한 Permission 확인
     */
    suspend fun checkPermission() {
        val permission = TedPermission.create()
            .setPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
            .check()

        if(permission.isGranted) {
            MaterialDialog(this).show {
                icon(R.drawable.ic_baseline_warning_24)
                title(R.string.warning_title)
                message(R.string.warning_content)
                positiveButton {
                    this.dismiss()
                }
                cancelOnTouchOutside(false)
            }
        }
        else {
            finishAndRemoveTask()
        }
    }
}