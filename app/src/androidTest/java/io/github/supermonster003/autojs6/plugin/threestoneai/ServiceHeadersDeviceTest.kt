package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderSecretsUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderValueKind
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileDocument
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Custom request headers on the service details page (roadmap 1.8.0 P12.1 - P12.3, D161 - D169):
 * the "Request headers" page and its add / edit dialog, every validation message, secret values
 * that are never shown again, the common presets, the OpenCode Go session header hint, the
 * MissingSessionID explanation and "Not set" after an import. The page is built from Android
 * views, so the test drives it through ActivityScenario like [ServiceDetailDeviceTest]; the row
 * menu and the "Add" row call the same editor methods the popup and the row click call.
 *
 * No request is sent. Secret values are placeholders that are never real credentials. Every
 * service the test creates or imports is deleted again and the base URL history is put back.
 */
class ServiceHeadersDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application get() = context.applicationContext as ThreeStoneAiApplication

    /** Services created or imported by the running test; deleted afterwards. */
    private val created = mutableListOf<String>()

    /** Saving a service records its base URL in the address history; each test puts it back. */
    private var baseUrlHistory: Map<String, *> = emptyMap<String, Any>()

    @Before
    fun keepBaseUrlHistory() {
        baseUrlHistory = baseUrlHistoryPreferences().all.toMap()
    }

    @After
    fun deleteServicesAndRestoreHistory() {
        created.forEach { id -> runCatching { application.onlineProfileRegistry.delete(id) } }
        // Written synchronously: the process may end right after the last test.
        baseUrlHistoryPreferences().edit().clear().apply {
            baseUrlHistory.forEach { (key, value) -> if (value is String) putString(key, value) }
        }.commit()
    }

    @Test
    fun headersAreAddedEditedAndDeletedAndSecretValuesAreNeverShown() {
        val id = createProfile(emptyList())
        launch(id).use { scenario ->
            scenario.call { it.showHeadersPage() }
            instrumentation.waitForIdleSync()
            assertTrue("the empty note is shown", scenario.shows(R.string.service_headers_empty))

            // A fixed value through the "Add header" row.
            scenario.call { activity -> clickableRow(activity, R.string.service_headers_add).performClick() }
            waitUntil("the add dialog opens") { scenario.call { it.headerEditor.dialog?.isShowing == true } }
            scenario.fill(name = "X-Title", value = "AutoJs6 test")
            assertTrue("a valid entry closes the dialog", scenario.submit())
            val fixed = OnlineAiCustomHeader("X-Title", OnlineAiHeaderValueKind.FIXED, "AutoJs6 test")
            assertTrue(scenario.rowTexts("x-title").contains(scenario.entry(fixed, ServiceHeaderSecretState.SET)))
            assertFalse("the empty note is hidden", scenario.shows(R.string.service_headers_empty))

            // Editing fills the form with the entry and replaces it.
            scenario.call { it.headerEditor.applyRowAction(0, ServiceHeaderEditor.ACTION_EDIT) }
            waitUntil("the edit dialog opens") { scenario.call { it.headerEditor.dialog?.isShowing == true } }
            assertEquals("X-Title", scenario.fieldText(ServiceHeaderEditor.TAG_NAME))
            assertEquals("AutoJs6 test", scenario.fieldText(ServiceHeaderEditor.TAG_VALUE))
            assertEquals(
                ServiceHeaderEditor.PLAIN_VALUE_INPUT_TYPE,
                scenario.call { checkNotNull(it.dialog().field(ServiceHeaderEditor.TAG_VALUE).editText).inputType },
            )
            scenario.fill(name = null, value = "AutoJs6 edited")
            assertTrue(scenario.submit())
            val edited = fixed.copy(value = "AutoJs6 edited")
            assertTrue(scenario.rowTexts("x-title").contains(scenario.entry(edited, ServiceHeaderSecretState.SET)))

            // A name with "token" suggests a secret value: masked, never shown again.
            scenario.openDialog(null)
            scenario.fill(name = "X-Api-Token", value = null)
            assertTrue("the secret box follows the name", scenario.call { it.dialog().secretBox().isChecked })
            assertEquals(
                TextInputLayout.END_ICON_PASSWORD_TOGGLE,
                scenario.call { it.dialog().field(ServiceHeaderEditor.TAG_VALUE).endIconMode },
            )
            scenario.fill(name = null, value = PLACEHOLDER_SECRET)
            scenario.call { activity ->
                val dialog = activity.dialog()
                val input = checkNotNull(dialog.field(ServiceHeaderEditor.TAG_VALUE).editText)
                // A password input type, so keyboards that ignore the no-suggestions flag offer none.
                assertEquals(ServiceHeaderEditor.SECRET_VALUE_INPUT_TYPE, input.inputType)
                assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, input.inputType and InputType.TYPE_MASK_VARIATION)
                assertTrue(input.transformationMethod is PasswordTransformationMethod)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    assertTrue(
                        "keyboards do not learn header values",
                        input.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0,
                    )
                }
                // Turning the box off and on again keeps the text, the cursor and the font.
                val font = input.typeface
                input.setSelection(input.length())
                dialog.secretBox().isChecked = false
                assertEquals(ServiceHeaderEditor.PLAIN_VALUE_INPUT_TYPE, input.inputType)
                assertFalse(input.transformationMethod is PasswordTransformationMethod)
                assertEquals(PLACEHOLDER_SECRET, input.text.toString())
                assertEquals(PLACEHOLDER_SECRET.length, input.selectionEnd)
                assertTrue(font === input.typeface)
                dialog.secretBox().isChecked = true
                assertEquals(ServiceHeaderEditor.SECRET_VALUE_INPUT_TYPE, input.inputType)
                assertTrue(input.transformationMethod is PasswordTransformationMethod)
                assertEquals(PLACEHOLDER_SECRET.length, input.selectionEnd)
                assertTrue(font === input.typeface)
            }
            assertTrue(scenario.submit())
            val secret = OnlineAiCustomHeader("X-Api-Token", OnlineAiHeaderValueKind.SECRET)
            assertTrue(scenario.rowTexts("x-api-token").contains(scenario.entry(secret, ServiceHeaderSecretState.TYPED)))
            assertEquals(setOf("x-api-token"), scenario.call { it.headerEditor.typedSecretKeys })
            assertFalse("a typed secret value is not shown", scenario.showsAnywhere(PLACEHOLDER_SECRET))

            // A per-conversation value has no value field.
            scenario.openDialog(null)
            scenario.fill(name = "X-Session-Id", value = null, conversation = true)
            assertEquals(View.GONE, scenario.call { it.dialog().field(ServiceHeaderEditor.TAG_VALUE).visibility })
            assertEquals(View.GONE, scenario.call { it.dialog().secretBox().visibility })
            assertTrue(scenario.submit())
            val conversation = OnlineAiCustomHeader("X-Session-Id", OnlineAiHeaderValueKind.CONVERSATION)
            assertTrue(scenario.rowTexts("x-session-id").contains(scenario.entry(conversation, ServiceHeaderSecretState.SET)))

            // Saving stores the names and kinds; the secret value goes to the encrypted store only.
            assertTrue("saving succeeds", scenario.call { it.save() })
            assertEquals(listOf(edited, secret, conversation), profile(id).headers)
            assertEquals(setOf("x-api-token"), application.onlineProfileRegistry.configuredHeaderSecrets(id))
            assertEquals(0, scenario.call { it.headerEditor.typedSecretCount })
            assertTrue(scenario.rowTexts("x-api-token").contains(scenario.entry(secret, ServiceHeaderSecretState.SET)))
            assertFalse("a stored secret value is not shown", scenario.showsAnywhere(PLACEHOLDER_SECRET))

            // Editing a stored secret starts empty; leaving it empty keeps the stored value.
            scenario.call { it.headerEditor.applyRowAction(1, ServiceHeaderEditor.ACTION_EDIT) }
            waitUntil("the edit dialog opens") { scenario.call { it.headerEditor.dialog?.isShowing == true } }
            assertEquals("", scenario.fieldText(ServiceHeaderEditor.TAG_VALUE))
            assertEquals(
                scenario.call { it.getString(R.string.service_headers_value_secret_keep) },
                scenario.call { it.dialog().field(ServiceHeaderEditor.TAG_VALUE).helperText?.toString() },
            )
            assertFalse("the edit dialog does not show the value", scenario.showsAnywhere(PLACEHOLDER_SECRET))
            assertTrue(scenario.submit())
            assertEquals(0, scenario.call { it.headerEditor.typedSecretCount })
            assertTrue(scenario.rowTexts("x-api-token").contains(scenario.entry(secret, ServiceHeaderSecretState.SET)))

            // Deleting from the row menu, then saving.
            scenario.call { it.headerEditor.applyRowAction(0, ServiceHeaderEditor.ACTION_DELETE) }
            val deleted = scenario.call { it.getString(R.string.service_headers_deleted, "X-Title") }
            waitUntil("the deletion is confirmed") { scenario.showsText(deleted) }
            assertTrue(scenario.call { it.window.decorView.findViewWithTag<View>("x-title") == null })
            assertTrue(scenario.call { it.hasUnsavedChanges() })
            assertTrue(scenario.call { it.save() })
            assertEquals(listOf(secret, conversation), profile(id).headers)

            scenario.call { it.headerEditor.applyRowAction(0, ServiceHeaderEditor.ACTION_DELETE) }
            assertTrue(scenario.call { it.save() })
            assertEquals(listOf(conversation), profile(id).headers)
            assertEquals(emptySet<String>(), application.onlineProfileRegistry.configuredHeaderSecrets(id))

            // The details page row lists what is configured.
            scenario.call { it.showDetailsPage() }
            instrumentation.waitForIdleSync()
            assertEquals(
                scenario.call { ServiceHeaderTexts(it).summary(listOf(conversation)) },
                scenario.rowSummary(R.string.service_headers_title),
            )
        }
    }

    @Test
    fun everyInvalidEntryKeepsTheDialogOpenAndSaysWhy() {
        val id = createProfile(listOf(OnlineAiCustomHeader("X-Title", OnlineAiHeaderValueKind.FIXED, "a")))
        launch(id).use { scenario ->
            scenario.call { it.showHeadersPage() }
            fun text(@StringRes resource: Int, vararg arguments: Any): String =
                scenario.call { it.getString(resource, *arguments) }
            val accent = "caf" + 0xE9.toChar()
            val cases = listOf(
                Invalid("empty name", "", "v", nameError = text(R.string.service_headers_problem_name_empty)),
                Invalid("space in the name", "X Title", "v", nameError = text(R.string.service_headers_problem_name_invalid)),
                Invalid(
                    "header sent by the plugin", "Authorization", "v", secret = false,
                    nameError = text(R.string.service_headers_problem_name_reserved_plugin),
                ),
                Invalid(
                    "header of the HTTP connection", "Content-Length", "v",
                    nameError = text(R.string.service_headers_problem_name_reserved_transport),
                ),
                Invalid(
                    "65 byte name", "X-" + "n".repeat(63), "v",
                    nameError = text(R.string.service_headers_problem_name_too_long, 64),
                ),
                Invalid("same name in another case", "x-title", "v", nameError = text(R.string.service_headers_problem_name_duplicate)),
                Invalid("empty value", "X-Empty", "", valueError = text(R.string.service_headers_problem_value_empty)),
                Invalid("non-ASCII value", "X-Accent", accent, valueError = text(R.string.service_headers_problem_value_invalid)),
                Invalid(
                    "1025 byte value", "X-Long", "v".repeat(1025),
                    valueError = text(R.string.service_headers_problem_value_too_long, 1024),
                ),
                Invalid(
                    "empty secret value", "X-Secret-Token", "", secret = true,
                    valueError = text(R.string.service_headers_problem_value_empty),
                ),
                Invalid(
                    "non-ASCII secret value", "X-Secret-Token", "cl" + 0xE9.toChar(), secret = true,
                    valueError = text(R.string.service_headers_problem_value_invalid),
                ),
            )
            for (case in cases) {
                scenario.openDialog(null)
                scenario.fill(name = case.name, value = case.value, secret = case.secret)
                assertFalse("${case.label}: the dialog stays open", scenario.submit())
                val errors = scenario.errors()
                assertEquals("${case.label}: name", case.nameError, errors.name)
                assertEquals("${case.label}: value", case.valueError, errors.value)
                assertNull("${case.label}: list", errors.list)
            }
            scenario.call { it.headerEditor.close() }
            instrumentation.waitForIdleSync()
            assertEquals(1, scenario.call { it.headerEditor.headers.size })
            assertEquals(0, scenario.call { it.headerEditor.typedSecretCount })
            assertFalse(scenario.call { it.hasUnsavedChanges() })
        }

        // Names plus values over 8192 bytes: a message for the whole list.
        val fills = (1..7).map { index -> OnlineAiCustomHeader("X-Fill-$index", OnlineAiHeaderValueKind.FIXED, "v".repeat(1024)) }
        val full = createProfile(listOf(OnlineAiCustomHeader("X-Title", OnlineAiHeaderValueKind.FIXED, "a")) + fills)
        launch(full).use { scenario ->
            scenario.call { it.showHeadersPage() }
            scenario.openDialog(null)
            scenario.fill(name = "X-Fill-8", value = "v".repeat(1024))
            assertFalse(scenario.submit())
            val errors = scenario.errors()
            assertNull(errors.name)
            assertNull(errors.value)
            assertEquals(
                scenario.call { it.getString(R.string.service_headers_problem_total_too_large, 8192) },
                errors.list,
            )
        }
    }

    @Test
    fun limitsRenamedSecretsAndStoredValuesAreChecked() {
        // 16 headers: the add row is disabled and the dialog does not open.
        val sixteen = createProfile(
            (1..16).map { index -> OnlineAiCustomHeader("X-H-$index", OnlineAiHeaderValueKind.FIXED, "v") },
        )
        launch(sixteen).use { scenario ->
            scenario.call { it.showHeadersPage() }
            instrumentation.waitForIdleSync()
            val limit = scenario.call { it.getString(R.string.service_headers_limit_reached, 16) }
            assertFalse(scenario.call { activity -> clickableRow(activity, R.string.service_headers_add).isEnabled })
            assertTrue(scenario.call { activity -> rowTextsOf(clickableRow(activity, R.string.service_headers_add)).contains(limit) })
            scenario.call { it.headerEditor.showDialog(null) }
            // The add row already says it; the refused dialog repeats it in a snackbar.
            waitUntil("the limit is explained") { scenario.countText(limit) >= 2 }
            assertNull(scenario.call { it.headerEditor.dialog })
        }

        // Renaming a stored secret needs the value again.
        val renamed = createProfile(
            listOf(OnlineAiCustomHeader("X-Api-Token", OnlineAiHeaderValueKind.SECRET)),
            secrets = mapOf("x-api-token" to PLACEHOLDER_SECRET),
        )
        launch(renamed).use { scenario ->
            scenario.call { it.showHeadersPage() }
            instrumentation.waitForIdleSync()
            val secret = OnlineAiCustomHeader("X-Api-Token", OnlineAiHeaderValueKind.SECRET)
            assertTrue(scenario.rowTexts("x-api-token").contains(scenario.entry(secret, ServiceHeaderSecretState.SET)))
            scenario.call { it.headerEditor.applyRowAction(0, ServiceHeaderEditor.ACTION_EDIT) }
            waitUntil("the edit dialog opens") { scenario.call { it.headerEditor.dialog?.isShowing == true } }
            scenario.fill(name = "X-Other-Token", value = null)
            assertFalse(scenario.submit())
            assertEquals(
                scenario.call { it.getString(R.string.service_headers_problem_secret_renamed) },
                scenario.errors().value,
            )
        }

        // The dialog does not know the size of stored secret values; the save names the problem (T70).
        val fills = (1..7).map { index -> OnlineAiCustomHeader("X-Fill-$index", OnlineAiHeaderValueKind.FIXED, "v".repeat(1000)) }
        val large = createProfile(
            listOf(OnlineAiCustomHeader("X-Big-Token", OnlineAiHeaderValueKind.SECRET)) + fills,
            secrets = mapOf("x-big-token" to "k".repeat(1000)),
        )
        launch(large).use { scenario ->
            scenario.call { it.showHeadersPage() }
            scenario.openDialog(null)
            scenario.fill(name = "X-Last", value = "v".repeat(120))
            assertTrue("the dialog accepts it", scenario.submit())
            assertFalse("the save refuses it", scenario.call { it.save() })
            val message = scenario.call {
                it.getString(
                    R.string.service_headers_save_invalid_list,
                    it.getString(R.string.service_headers_problem_total_too_large, 8192),
                )
            }
            waitUntil("the save names the problem") { scenario.showsText(message) }
            assertEquals(8, profile(large).headers.size)
            assertEquals(setOf("x-big-token"), application.onlineProfileRegistry.configuredHeaderSecrets(large))
        }
    }

    @Test
    fun presetsAddReadyHeadersAndFillTheFormForSecretOnes() {
        val id = createProfile(emptyList())
        launch(id).use { scenario ->
            scenario.call { it.showHeadersPage() }
            scenario.openDialog(null)
            assertFalse("the presets start folded", scenario.presetShown(ServiceHeaderPreset.OPENROUTER))
            scenario.expandPresets()
            assertTrue(scenario.presetShown(ServiceHeaderPreset.OPENROUTER))

            // Ready presets are added at once.
            scenario.clickPreset(ServiceHeaderPreset.OPENROUTER)
            waitUntil("the preset closes the dialog") { scenario.call { it.headerEditor.dialog == null } }
            assertEquals(ServiceHeaderPreset.OPENROUTER.headers, scenario.call { it.headerEditor.headers })
            val added = scenario.call {
                it.getString(R.string.service_headers_preset_added, "HTTP-Referer, X-OpenRouter-Title")
            }
            waitUntil("the added names are confirmed") { scenario.showsText(added) }

            scenario.openDialog(null)
            scenario.expandPresets()
            scenario.clickPreset(ServiceHeaderPreset.OPENROUTER)
            assertEquals(
                scenario.call { it.getString(R.string.service_headers_preset_exists) },
                scenario.errors().list,
            )
            assertNotNull("the dialog stays open", scenario.call { it.headerEditor.dialog })

            // A preset with a secret value fills the form and waits for the value.
            scenario.clickPreset(ServiceHeaderPreset.HELICONE)
            assertEquals("Helicone-Auth", scenario.fieldText(ServiceHeaderEditor.TAG_NAME))
            assertTrue(scenario.call { it.dialog().secretBox().isChecked })
            assertEquals(
                scenario.call { it.getString(R.string.service_headers_bearer_placeholder) },
                scenario.call { it.dialog().field(ServiceHeaderEditor.TAG_VALUE).placeholderText?.toString() },
            )
            assertFalse("the presets fold again", scenario.presetShown(ServiceHeaderPreset.HELICONE))
            assertFalse(scenario.submit())
            assertEquals(scenario.call { it.getString(R.string.service_headers_problem_value_empty) }, scenario.errors().value)
            scenario.fill(name = null, value = PLACEHOLDER_SECRET)
            assertTrue(scenario.submit())
            assertEquals(setOf("helicone-auth"), scenario.call { it.headerEditor.typedSecretKeys })

            scenario.openDialog(null)
            scenario.expandPresets()
            scenario.clickPreset(ServiceHeaderPreset.OPENCODE_GO)
            waitUntil("the session preset closes the dialog") { scenario.call { it.headerEditor.dialog == null } }

            scenario.openDialog(null)
            scenario.expandPresets()
            scenario.clickPreset(ServiceHeaderPreset.CLOUDFLARE_AI_GATEWAY)
            assertEquals("cf-aig-authorization", scenario.fieldText(ServiceHeaderEditor.TAG_NAME))
            assertTrue(scenario.call { it.dialog().secretBox().isChecked })
            scenario.call { it.dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            waitUntil("cancel closes the dialog") { scenario.call { it.headerEditor.dialog == null } }

            assertEquals(
                ServiceHeaderPreset.OPENROUTER.headers + ServiceHeaderPreset.HELICONE.headers +
                    ServiceHeaderPreset.OPENCODE_GO.headers,
                scenario.call { it.headerEditor.headers },
            )
            assertFalse(scenario.showsAnywhere(PLACEHOLDER_SECRET))
        }
        // Closed without saving: nothing was stored.
        assertEquals(emptyList<OnlineAiCustomHeader>(), profile(id).headers)
        assertEquals(emptySet<String>(), application.onlineProfileRegistry.configuredHeaderSecrets(id))
    }

    @Test
    fun openCodeAddressesOfferTheSessionHeader() {
        val session = ServiceHeaderPreset.OPENCODE_GO.headers
        launch(null).use { scenario ->
            fun hint(): Int = scenario.call {
                it.window.decorView.findViewWithTag<View>(ServiceDetailActivity.TAG_OPENCODE_HINT).visibility
            }
            fun address(value: String) = scenario.call { activity -> baseUrlField(activity).setText(value) }
            address(OPENCODE_OPENAI)
            assertEquals("OpenAI compatible endpoint", View.VISIBLE, hint())
            address(BASE_URL)
            assertEquals("other hosts", View.GONE, hint())
            address(OPENCODE_ANTHROPIC)
            assertEquals("Anthropic endpoint", View.VISIBLE, hint())

            scenario.call { activity ->
                val hintView = activity.window.decorView.findViewWithTag<View>(ServiceDetailActivity.TAG_OPENCODE_HINT)
                views(hintView).filterIsInstance<Button>().single().performClick()
            }
            assertEquals(session, scenario.call { it.headerEditor.headers })
            assertEquals(View.GONE, hint())
            val sessionAdded = scenario.call {
                it.getString(R.string.service_headers_session_added, ServiceHeaderPolicy.SESSION_HEADER_NAME)
            }
            waitUntil("adding is confirmed") { scenario.showsText(sessionAdded) }
            assertFalse(scenario.call { it.headerEditor.addSessionHeader() })
            val exists = scenario.call {
                it.getString(R.string.service_headers_session_exists, ServiceHeaderPolicy.SESSION_HEADER_NAME)
            }
            waitUntil("a second add is refused") { scenario.showsText(exists) }
            assertEquals(session, scenario.call { it.headerEditor.headers })
        }

        // The MissingSessionID entry of the services page and the chat opens the page with the header added.
        val saved = createProfile(emptyList(), baseUrl = OPENCODE_OPENAI)
        launch(saved, addSessionHeader = true).use { scenario ->
            assertEquals(session, scenario.call { it.headerEditor.headers })
            assertTrue("the header is a draft change", scenario.call { it.hasUnsavedChanges() })
            assertEquals(
                View.GONE,
                scenario.call { it.window.decorView.findViewWithTag<View>(ServiceDetailActivity.TAG_OPENCODE_HINT).visibility },
            )
        }

        // The connection test failure explains MissingSessionID and offers to add the header.
        val google = createProfile(emptyList(), baseUrl = OPENCODE_GOOGLE)
        launch(google).use { scenario ->
            val failure = OnlineAiFailureException(OnlineAiFailureReason.REQUEST_REJECTED, 400, MISSING_SESSION_DETAIL)
            val dialog = scenario.call { it.tester.showFailure(google, failure) }
            instrumentation.waitForIdleSync()
            val hintText = scenario.call { it.getString(R.string.service_headers_missing_session_hint) }
            val message = scenario.call { dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty() }
            assertTrue("the provider's detail is kept: $message", message.contains("MissingSessionID"))
            assertTrue("the hint is appended: $message", message.contains(hintText))
            val add = scenario.call { dialog.getButton(AlertDialog.BUTTON_NEUTRAL) }
            assertEquals(View.VISIBLE, scenario.call { add.visibility })
            assertTrue(
                scenario.call {
                    add.text.toString().equals(it.getString(R.string.service_headers_add_session_action), ignoreCase = true)
                },
            )
            scenario.call { add.performClick() }
            instrumentation.waitForIdleSync()
            assertEquals(session, scenario.call { it.headerEditor.headers })

            val other = OnlineAiFailureException(OnlineAiFailureReason.AUTHENTICATION_FAILED, 401, "Invalid API key")
            val plain = scenario.call { it.tester.showFailure(google, other) }
            instrumentation.waitForIdleSync()
            val plainMessage = scenario.call { plain.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty() }
            assertFalse(plainMessage.contains(hintText))
            assertTrue(
                "no add button for other failures",
                scenario.call { plain.getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility != View.VISIBLE },
            )
            scenario.call { plain.dismiss() }
        }
    }

    @Test
    fun importedSecretHeadersAskForTheirValues() {
        val name = "Headers import P12.5"
        val source = OnlineAiProfile.create(
            displayName = name,
            provider = OnlineAiProvider.OPENAI_COMPATIBLE,
            baseUrl = BASE_URL,
            modelId = MODEL_ID,
            headers = listOf(
                OnlineAiCustomHeader("X-Title", OnlineAiHeaderValueKind.FIXED, "AutoJs6"),
                OnlineAiCustomHeader("Helicone-Auth", OnlineAiHeaderValueKind.SECRET),
            ),
        )
        val file = File(context.cacheDir, "service-headers-import-test.json")
        file.writeBytes(OnlineAiProfileCodec.encode(OnlineAiProfileDocument(revision = 1L, profiles = listOf(source))))
        val before = profileIds()
        val imported: String
        try {
            ActivityScenario.launch<ModelManagerActivity>(Intent(context, ModelManagerActivity::class.java)).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.call { it.importProfiles(Uri.fromFile(file)) }
                val confirmation = scenario.call {
                    it.getString(R.string.online_ai_import_succeeded, 1) + " " +
                        it.getString(R.string.service_headers_import_secrets)
                }
                waitUntil("the import asks for the secret values") { scenario.showsText(confirmation) }
                imported = (profileIds() - before.toSet()).single()
                created += imported
                val shownName = profile(imported).displayName
                val missingKey = scenario.call { it.getString(R.string.models_badge_missing_key) }
                val missingHeader = scenario.call { it.getString(R.string.service_headers_badge_missing) }
                waitUntil("the imported service's card shows both badges") {
                    scenario.call { activity ->
                        val title = views(activity.window.decorView).filterIsInstance<TextView>()
                            .firstOrNull { it.text.toString() == shownName }
                        val card = title?.let {
                            generateSequence(it.parent as? View) { view -> view.parent as? View }
                                .firstOrNull { container -> rowTextsOf(container).contains(missingKey) }
                        }
                        card != null && rowTextsOf(card).contains(missingHeader)
                    }
                }
            }
        } finally {
            file.delete()
        }
        assertEquals(emptySet<String>(), application.onlineProfileRegistry.configuredHeaderSecrets(imported))

        launch(imported).use { scenario ->
            val helicone = OnlineAiCustomHeader("Helicone-Auth", OnlineAiHeaderValueKind.SECRET)
            val missing = scenario.call { ServiceHeaderTexts(it).missingSecrets(listOf(helicone)) }
            val summary = scenario.rowSummary(R.string.service_headers_title)
            assertTrue("the details row names the missing value: $summary", summary.contains(missing))
            scenario.call { it.showHeadersPage() }
            instrumentation.waitForIdleSync()
            assertTrue(scenario.rowTexts("helicone-auth").contains(scenario.entry(helicone, ServiceHeaderSecretState.NOT_SET)))
            val title = OnlineAiCustomHeader("X-Title", OnlineAiHeaderValueKind.FIXED, "AutoJs6")
            assertTrue(scenario.rowTexts("x-title").contains(scenario.entry(title, ServiceHeaderSecretState.SET)))
        }
    }

    /**
     * The dialog's OK and Cancel follow the plugin's language, also when the system language is
     * written the other way (Material lays the button bar out by the process default locale).
     */
    @Test
    fun dialogActionsFollowTheAppLanguageDirection() {
        val restore = keepApplicationSettings()
        try {
            ApplicationSettingsStore(context).let { it.save(it.load().copy(language = AppLanguage.ARABIC)) }
            val id = createProfile(emptyList())
            launch(id).use { scenario ->
                assertEquals(View.LAYOUT_DIRECTION_RTL, scenario.call { it.resources.configuration.layoutDirection })
                scenario.call { it.showHeadersPage() }
                instrumentation.waitForIdleSync()
                scenario.openDialog(null)
                settle()
                scenario.call { activity ->
                    val dialog = activity.dialog()
                    assertEquals(View.LAYOUT_DIRECTION_RTL, dialog.root().layoutDirection)
                    val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    assertEquals(View.LAYOUT_DIRECTION_RTL, (positive.parent as View).layoutDirection)
                    val positiveX = IntArray(2).also(positive::getLocationOnScreen)[0]
                    val negativeX = IntArray(2).also(negative::getLocationOnScreen)[0]
                    assertTrue("OK is to the left of Cancel ($positiveX, $negativeX)", positiveX < negativeX)
                    negative.performClick()
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            restore()
        }
    }

    /**
     * Light and dark renderings for review (run with -e screenshots true). The page and the dialog
     * set FLAG_SECURE, so system screenshots are black; the views are drawn into bitmaps instead.
     */
    @Test
    fun lightAndDarkScreenshots() {
        assumeTrue(
            "Run with -e screenshots true",
            InstrumentationRegistry.getArguments().getString("screenshots") == "true",
        )
        val restore = keepApplicationSettings()
        val store = ApplicationSettingsStore(context)
        val original = store.load()
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), SCREENSHOT_DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val id = createProfile(
            listOf(
                OnlineAiCustomHeader("X-Title", OnlineAiHeaderValueKind.FIXED, "AutoJs6"),
                OnlineAiCustomHeader("X-Api-Token", OnlineAiHeaderValueKind.SECRET),
                OnlineAiCustomHeader("Helicone-Auth", OnlineAiHeaderValueKind.SECRET),
            ),
            secrets = mapOf("x-api-token" to PLACEHOLDER_SECRET),
            baseUrl = OPENCODE_OPENAI,
            name = "Headers screenshot",
        )
        try {
            for ((mode, darkMode) in listOf("light" to AppDarkMode.LIGHT, "dark" to AppDarkMode.DARK)) {
                store.save(original.copy(darkMode = darkMode))
                launch(id).use { scenario ->
                    settle()
                    scenario.call { renderPage(it, File(directory, "details-$mode.png")) }
                    scenario.call { it.headerEditor.addSessionHeader() }
                    scenario.call { it.showHeadersPage() }
                    settle()
                    scenario.call { render(it, null, File(directory, "headers-$mode.png")) }
                    scenario.openDialog(null)
                    scenario.expandPresets()
                    settle()
                    scenario.call { render(it, it.headerEditor.dialog, File(directory, "add-dialog-$mode.png")) }
                    scenario.call { it.headerEditor.showDialog(null) }
                    waitUntil("a new dialog opens") { scenario.call { it.headerEditor.dialog?.isShowing == true } }
                    scenario.fill(name = "Authorization", value = "v", secret = false)
                    scenario.submit()
                    settle()
                    scenario.call { render(it, it.headerEditor.dialog, File(directory, "error-dialog-$mode.png")) }
                    scenario.call { it.headerEditor.close() }
                    scenario.call { it.showDetailsPage() }
                    val failure = OnlineAiFailureException(OnlineAiFailureReason.REQUEST_REJECTED, 400, MISSING_SESSION_DETAIL)
                    val dialog = scenario.call { it.tester.showFailure(id, failure) }
                    settle()
                    scenario.call { render(it, dialog, File(directory, "missing-session-$mode.png")) }
                    scenario.call { dialog.dismiss() }
                }
                ActivityScenario.launch<ModelManagerActivity>(Intent(context, ModelManagerActivity::class.java)).use { scenario ->
                    settle()
                    SystemClock.sleep(SETTLE_MILLIS)
                    scenario.call { renderPage(it, File(directory, "services-$mode.png")) }
                }
            }
        } finally {
            restore()
        }
    }

    /** Keeps the appearance and language settings; the returned function writes them back. */
    private fun keepApplicationSettings(): () -> Unit {
        val preferences = context.getSharedPreferences("application-settings", Context.MODE_PRIVATE)
        val saved = preferences.all.toMap()
        return {
            val restored = preferences.edit().clear().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
            assertTrue("Appearance preferences written back", restored)
            assertEquals(saved, preferences.all)
        }
    }

    // ---- Services ---------------------------------------------------------------------------

    private fun createProfile(
        headers: List<OnlineAiCustomHeader>,
        secrets: Map<String, String> = emptyMap(),
        baseUrl: String = BASE_URL,
        name: String = "$NAME ${created.size + 1}",
    ): String {
        val profile = OnlineAiProfile.create(
            displayName = name,
            provider = OnlineAiProvider.OPENAI_COMPATIBLE,
            baseUrl = baseUrl,
            modelId = MODEL_ID,
            headers = headers,
        )
        val update = if (secrets.isEmpty()) {
            OnlineAiHeaderSecretsUpdate.Keep
        } else {
            OnlineAiHeaderSecretsUpdate.Put.takingOwnership(secrets.mapValues { (_, value) -> value.toCharArray() })
        }
        application.onlineProfileRegistry.save(profile, OnlineAiCredentialUpdate.Clear, update)
        created += profile.profileId
        return profile.profileId
    }

    private fun baseUrlHistoryPreferences() =
        context.getSharedPreferences("online-ai-base-url-history", Context.MODE_PRIVATE)

    private fun profileIds(): List<String> =
        application.onlineProfileRegistry.snapshot().profiles.map { it.profile.profileId }.sorted()

    private fun profile(id: String): OnlineAiProfile =
        application.onlineProfileRegistry.snapshot().profiles.first { it.profile.profileId == id }.profile

    // ---- Page -------------------------------------------------------------------------------

    private fun launch(profileId: String?, addSessionHeader: Boolean = false): ActivityScenario<ServiceDetailActivity> {
        val intent = ServiceDetailActivity.intent(context, profileId)
        if (addSessionHeader) intent.putExtra(ServiceDetailActivity.EXTRA_ADD_SESSION_HEADER, true)
        return ActivityScenario.launch<ServiceDetailActivity>(intent).also { instrumentation.waitForIdleSync() }
    }

    private fun baseUrlField(activity: ServiceDetailActivity): EditText {
        val hint = activity.getString(R.string.online_ai_base_url)
        return checkNotNull(
            views(activity.window.decorView).filterIsInstance<TextInputLayout>().first { it.hint?.toString() == hint }.editText,
        )
    }

    /** The clickable row whose title is [title]. */
    private fun clickableRow(activity: Activity, @StringRes title: Int): View {
        val text = activity.getString(title)
        var row: View = views(activity.window.decorView).filterIsInstance<TextView>()
            .first { it.isShown && it.text.toString() == text }
        while (!row.isClickable) row = row.parent as View
        return row
    }

    private fun rowTextsOf(root: View): List<String> =
        views(root).filterIsInstance<TextView>().map { it.text.toString() }

    /** The texts of the header row tagged with [key], or an empty list when there is none. */
    private fun ActivityScenario<ServiceDetailActivity>.rowTexts(key: String): List<String> = call { activity ->
        activity.window.decorView.findViewWithTag<View>(key)?.let(::rowTextsOf).orEmpty()
    }

    private fun ActivityScenario<ServiceDetailActivity>.entry(
        header: OnlineAiCustomHeader,
        state: ServiceHeaderSecretState,
    ): String = call { ServiceHeaderTexts(it).entry(header, state) }

    /** The summary under the settings row titled [title]. */
    private fun ActivityScenario<ServiceDetailActivity>.rowSummary(@StringRes title: Int): String = call { activity ->
        val text = activity.getString(title)
        val titleView = views(activity.window.decorView).filterIsInstance<TextView>()
            .first { it.isShown && it.text.toString() == text }
        val column = titleView.parent as android.view.ViewGroup
        (column.getChildAt(column.indexOfChild(titleView) + 1) as TextView).text.toString()
    }

    private fun ActivityScenario<ServiceDetailActivity>.shows(@StringRes text: Int): Boolean = call { activity ->
        val shown = activity.getString(text)
        views(activity.window.decorView).filterIsInstance<TextView>().any { it.isShown && it.text.toString() == shown }
    }

    private fun <A : Activity> ActivityScenario<A>.showsText(text: String): Boolean = call { activity ->
        views(activity.window.decorView).filterIsInstance<TextView>().any { it.isShown && it.text.toString() == text }
    }

    private fun <A : Activity> ActivityScenario<A>.countText(text: String): Int = call { activity ->
        views(activity.window.decorView).filterIsInstance<TextView>().count { it.isShown && it.text.toString() == text }
    }

    /** True when any text of the page or the open dialog contains [text]. */
    private fun ActivityScenario<ServiceDetailActivity>.showsAnywhere(text: String): Boolean = call { activity ->
        listOfNotNull(activity.window.decorView, activity.headerEditor.dialog?.window?.decorView)
            .flatMap(::views)
            .filterIsInstance<TextView>()
            .any { it.text?.toString()?.contains(text) == true }
    }

    // ---- Dialog -----------------------------------------------------------------------------

    private fun ServiceDetailActivity.dialog(): AlertDialog = checkNotNull(headerEditor.dialog) { "No header dialog" }

    private fun Dialog.root(): View = checkNotNull(window).decorView

    private fun Dialog.field(tag: String): TextInputLayout = root().findViewWithTag(tag)

    private fun Dialog.secretBox(): MaterialCheckBox = root().findViewWithTag(ServiceHeaderEditor.TAG_SECRET)

    private fun ActivityScenario<ServiceDetailActivity>.openDialog(index: Int?) {
        call { it.headerEditor.showDialog(index) }
        waitUntil("the header dialog opens") { call { it.headerEditor.dialog?.isShowing == true } }
    }

    private fun ActivityScenario<ServiceDetailActivity>.fieldText(tag: String): String =
        call { it.dialog().field(tag).editText?.text?.toString().orEmpty() }

    /** Fills the open dialog; null leaves a field as it is. */
    private fun ActivityScenario<ServiceDetailActivity>.fill(
        name: String?,
        value: String?,
        conversation: Boolean = false,
        secret: Boolean? = null,
    ) {
        call { activity ->
            val dialog = activity.dialog()
            val mode = activity.getString(
                if (conversation) R.string.service_headers_kind_conversation else R.string.service_headers_mode_value,
            )
            val radio = views(dialog.root()).filterIsInstance<MaterialRadioButton>().first { it.text.toString() == mode }
            if (!radio.isChecked) radio.performClick()
            name?.let { checkNotNull(dialog.field(ServiceHeaderEditor.TAG_NAME).editText).setText(it) }
            secret?.let { dialog.secretBox().isChecked = it }
            value?.let { checkNotNull(dialog.field(ServiceHeaderEditor.TAG_VALUE).editText).setText(it) }
        }
        instrumentation.waitForIdleSync()
    }

    /** Presses OK; true when the dialog closed. */
    private fun ActivityScenario<ServiceDetailActivity>.submit(): Boolean {
        call { it.dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        instrumentation.waitForIdleSync()
        return call { it.headerEditor.dialog == null }
    }

    private fun ActivityScenario<ServiceDetailActivity>.errors(): FormErrors = call { activity ->
        val dialog = activity.dialog()
        val list = dialog.root().findViewWithTag<TextView>(ServiceHeaderEditor.TAG_ERROR)
        FormErrors(
            name = dialog.field(ServiceHeaderEditor.TAG_NAME).error?.toString(),
            value = dialog.field(ServiceHeaderEditor.TAG_VALUE).error?.toString(),
            list = list.takeIf { it.visibility == View.VISIBLE }?.text?.toString(),
        )
    }

    private fun ActivityScenario<ServiceDetailActivity>.expandPresets() {
        call { it.dialog().root().findViewWithTag<View>(ServiceHeaderEditor.TAG_PRESETS).performClick() }
        instrumentation.waitForIdleSync()
    }

    private fun ActivityScenario<ServiceDetailActivity>.presetShown(preset: ServiceHeaderPreset): Boolean =
        call { it.dialog().root().findViewWithTag<View>(preset.name).isShown }

    private fun ActivityScenario<ServiceDetailActivity>.clickPreset(preset: ServiceHeaderPreset) {
        call { it.dialog().root().findViewWithTag<View>(preset.name).performClick() }
        instrumentation.waitForIdleSync()
    }

    // ---- Rendering --------------------------------------------------------------------------

    private fun settle() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(FRAME_MILLIS)
        instrumentation.waitForIdleSync()
    }

    /** The visible window, with [dialog] drawn over a dimmed page where it sits on screen. */
    private fun render(activity: Activity, dialog: Dialog?, file: File) {
        val decor = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decor.draw(canvas)
        dialog?.window?.decorView?.let { dialogRoot ->
            canvas.drawColor(Color.argb(DIM_ALPHA, 0, 0, 0))
            val pageOrigin = IntArray(2).also(decor::getLocationOnScreen)
            val dialogOrigin = IntArray(2).also(dialogRoot::getLocationOnScreen)
            canvas.save()
            canvas.translate((dialogOrigin[0] - pageOrigin[0]).toFloat(), (dialogOrigin[1] - pageOrigin[1]).toFloat())
            dialogRoot.draw(canvas)
            canvas.restore()
        }
        write(bitmap, file)
    }

    /** The whole scrolling page, also the part below the screen, on the window background. */
    private fun renderPage(activity: Activity, file: File) {
        val decor = activity.window.decorView
        val scroll = views(decor).filterIsInstance<ScrollView>().filter { it.isShown }.maxBy { it.height }
        val content = scroll.getChildAt(0)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decor.background?.constantState?.newDrawable(activity.resources)?.mutate()?.apply {
            setBounds(0, 0, content.width, content.height)
            draw(canvas)
        }
        content.draw(canvas)
        write(bitmap, file)
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        bitmap.recycle()
    }

    private fun <A : Activity, T> ActivityScenario<A>.call(block: (A) -> T): T {
        var result: Result<T>? = null
        onActivity { activity -> result = runCatching { block(activity) } }
        return checkNotNull(result).getOrThrow()
    }

    private data class FormErrors(val name: String?, val value: String?, val list: String?)

    private class Invalid(
        val label: String,
        val name: String,
        val value: String,
        val secret: Boolean? = null,
        val nameError: String? = null,
        val valueError: String? = null,
    )

    private companion object {
        const val NAME = "Headers P12.5"
        const val BASE_URL = "https://headers.example.test/v1"
        const val MODEL_ID = "headers-test-model"
        const val OPENCODE_OPENAI = "https://opencode.ai/inference/go/openai/v1"
        const val OPENCODE_ANTHROPIC = "https://opencode.ai/inference/go/anthropic/v1"
        const val OPENCODE_GOOGLE = "https://opencode.ai/inference/go/google/v1beta"

        /** OpenCode Go's answer without the session header (2026-11-15), as a provider detail. */
        const val MISSING_SESSION_DETAIL = """{"type":"error","error":{"type":"MissingSessionID","message":"x-opencode-session header is required"}}"""

        /** Typed into secret value fields; it is not a credential and is never sent. */
        const val PLACEHOLDER_SECRET = "placeholder-not-a-secret-0000"
        const val SCREENSHOT_DIRECTORY = "header-screenshots"
        const val FRAME_MILLIS = 400L
        const val SETTLE_MILLIS = 1_500L
        const val DIM_ALPHA = 110
    }
}
