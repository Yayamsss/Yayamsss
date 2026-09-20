package com.lnreader.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import lnreader.platform.AndroidRuntimeContext
import lnreader.ui.LNReaderApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidRuntimeContext.initialize(applicationContext)
        setContent {
            LNReaderApp()
        }
    }
}
