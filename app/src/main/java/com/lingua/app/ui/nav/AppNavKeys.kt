package com.lingua.app.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The three-tab shell (translate / history / settings). */
@Serializable data object HomeKey : NavKey

/** Full-screen API endpoint editor. `null` means "create a new one". */
@Serializable data class ApiEditorKey(val profileId: String? = null) : NavKey
