package com.huang1988pioneer.mediaconverter

import android.app.Application

class ConverterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.attach(this)
        Session.prepare(this)
    }
}
