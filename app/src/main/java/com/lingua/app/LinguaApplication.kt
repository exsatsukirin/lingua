package com.lingua.app

import android.app.Application

class LinguaApplication : Application() {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}

/** Shorthand used by ViewModel factories. */
val Application.appContainer: AppContainer
  get() = (this as LinguaApplication).container
