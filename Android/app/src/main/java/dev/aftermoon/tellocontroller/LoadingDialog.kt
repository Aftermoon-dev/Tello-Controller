package dev.aftermoon.tellocontroller

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import dev.aftermoon.tellocontroller.databinding.DialogLoadingBinding

class LoadingDialog(context: Context): Dialog(context) {
    private val viewBinding: DialogLoadingBinding

    init {
        setCanceledOnTouchOutside(false)
        setCancelable(false)
        window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        viewBinding = DialogLoadingBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
    }
}