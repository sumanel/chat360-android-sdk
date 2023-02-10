package com.chat360.chatbot.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import com.chat360.chatbot.R
import com.google.android.material.snackbar.Snackbar

class Chat360SnackBarHelper {
    private fun startInstalledAppDetailsActivity(context: Context) {
        val i = Intent()
        i.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        i.addCategory(Intent.CATEGORY_DEFAULT)
        i.data = Uri.parse("package:" + context.packageName)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(i)
    }

    fun showSnackBarWithSettingAction(context: Context, view: View, message: String) {
        Snackbar.make(
            view, message, Snackbar.LENGTH_LONG
        ).setAction(
            context.getString(R.string.settings)
        ) { v: View? ->
            startInstalledAppDetailsActivity(
                context
            )
        }.show()
    }

    fun showMessageInSnackBar(view: View, message: String) {
        Snackbar.make(
            view, message, Snackbar.LENGTH_LONG
        ).setAction("") { v: View? -> }.show()
    }
}