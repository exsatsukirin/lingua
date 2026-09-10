package com.lingua.app.ui.settings

import com.lingua.app.data.settings.ApiProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unsaved-changes detection for the endpoint editor. */
class ApiEditorUiStateTest {

  private val loaded =
    ApiProfileForm(
      name = "DeepSeek",
      baseUrl = "https://api.deepseek.com/v1",
      apiKey = "sk-test",
      model = "deepseek-flash",
      temperature = 0.2,
      timeoutSeconds = 60,
      jsonMode = true,
      headersText = "",
    )

  /** A state that mirrors [loaded] field for field. */
  private fun loadedState(baseline: ApiProfileForm? = loaded) =
    ApiEditorUiState(
      profileId = "p1",
      name = loaded.name,
      baseUrl = loaded.baseUrl,
      apiKey = loaded.apiKey,
      model = loaded.model,
      temperature = loaded.temperature,
      timeoutSeconds = loaded.timeoutSeconds,
      jsonMode = loaded.jsonMode,
      headersText = loaded.headersText,
      baseline = baseline,
    )

  @Test
  fun `a freshly loaded profile is not dirty`() {
    assertFalse(loadedState().isDirty)
  }

  @Test
  fun `an existing profile is not dirty while it is still loading`() {
    assertFalse(loadedState(baseline = null).isDirty)
  }

  @Test
  fun `each editable field marks the form dirty`() {
    assertTrue(loadedState().copy(name = "Renamed").isDirty)
    assertTrue(loadedState().copy(baseUrl = "https://api.deepseek.com").isDirty)
    assertTrue(loadedState().copy(apiKey = "sk-other").isDirty)
    assertTrue(loadedState().copy(model = "deepseek-v4-pro").isDirty)
    assertTrue(loadedState().copy(temperature = 0.7).isDirty)
    assertTrue(loadedState().copy(timeoutSeconds = 120).isDirty)
    assertTrue(loadedState().copy(jsonMode = false).isDirty)
    assertTrue(loadedState().copy(headersText = "X-Test: 1").isDirty)
  }

  @Test
  fun `reverting an edit clears the dirty flag`() {
    val edited = loadedState().copy(model = "deepseek-v4-pro")
    assertTrue(edited.isDirty)
    assertFalse(edited.copy(model = loaded.model).isDirty)
  }

  @Test
  fun `an untouched new profile is not dirty`() {
    val blank = ApiEditorUiState(baseline = ApiProfileForm())
    assertFalse(blank.isDirty)
  }

  @Test
  fun `typing into a new profile marks it dirty`() {
    val blank = ApiEditorUiState(baseline = ApiProfileForm())
    assertTrue(blank.copy(name = "My endpoint").isDirty)
  }

  @Test
  fun `applying a preset to a new profile marks it dirty`() {
    val blank = ApiEditorUiState(baseline = ApiProfileForm())
    val preset = ProviderPreset.DeepSeek
    assertTrue(blank.copy(baseUrl = preset.baseUrl, model = preset.model).isDirty)
  }

  @Test
  fun `transient state does not count as an edit`() {
    // Testing a connection, fetching models or showing validation errors must not block leaving.
    val busy =
      loadedState().copy(
        test = TestState.Running,
        isFetchingModels = true,
        modelsMessage = "OK (2)",
        models = listOf("deepseek-flash"),
        validation = ValidationErrors(name = true),
        keyDecryptFailed = true,
      )
    assertFalse(busy.isDirty)
  }

  @Test
  fun `form snapshot mirrors the editable fields only`() {
    val form = loadedState().form
    assertTrue(form == loaded)
    assertTrue(form != loadedState().copy(name = "Other").form)
  }

  @Test
  fun `defaults of a blank form match a new profile`() {
    val blank = ApiProfileForm()
    assertFalse(blank.name.isNotEmpty())
    assertTrue(blank.baseUrl.isEmpty())
    assertTrue(blank.model.isEmpty())
    assertTrue(blank.jsonMode)
    assertTrue(blank.temperature == ApiProfile.DEFAULT_TEMPERATURE)
    assertTrue(blank.timeoutSeconds == ApiProfile.DEFAULT_TIMEOUT_SECONDS)
  }
}
