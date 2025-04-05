package eu.depau.etchdroid.plugins.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import eu.depau.etchdroid.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.compose.SentryTraced
import eu.depau.etchdroid.plugins.telemetry.DummyTelemetry.telemetryTag as dummyTelemetryTag

internal const val SENTRY_DSN =
    "https://39a6e220c97c585acd25ced5a6855b4d@o4508123221590016.ingest.de.sentry.io/4508123222704209"

internal const val SAMPLE_RATE = 0.1
internal const val ERROR_SAMPLE_RATE = 1.0

internal const val PREFS_NAME = "telemetry"
internal const val PREFS_ENABLED_KEY = "telemetry_enabled"

internal fun TelemetryLevel.toSentry(): SentryLevel = when (this) {
    TelemetryLevel.DEBUG -> SentryLevel.DEBUG
    TelemetryLevel.INFO -> SentryLevel.INFO
    TelemetryLevel.WARNING -> SentryLevel.WARNING
    TelemetryLevel.ERROR -> SentryLevel.ERROR
    TelemetryLevel.FATAL -> SentryLevel.FATAL
}

internal fun SentryLevel.toWrapper(): TelemetryLevel = when (this) {
    SentryLevel.DEBUG -> TelemetryLevel.DEBUG
    SentryLevel.INFO -> TelemetryLevel.INFO
    SentryLevel.WARNING -> TelemetryLevel.WARNING
    SentryLevel.ERROR -> TelemetryLevel.ERROR
    SentryLevel.FATAL -> TelemetryLevel.FATAL
}

internal fun TelemetryBreadcrumb.toSentry(): Breadcrumb {
    val sentry = if (timestamp != null) {
        Breadcrumb(timestamp)
    } else {
        Breadcrumb()
    }

    sentry.message = message
    sentry.category = category
    sentry.level = level?.toSentry()
    sentry.type = type
    @Suppress("UnstableApiUsage")
    sentry.data.putAll(data)
    sentry.origin = origin

    return sentry
}

internal fun TelemetryBreadcrumb.toFirebase(): CustomKeysAndValues {
    return CustomKeysAndValues.Builder().apply {
        message?.let { putString("message", it) }
        category?.let { putString("category", it) }
        level?.let { putString("level", it.name) }
        type?.let { putString("type", it) }
        data.forEach { (key, value) -> putString(key, value.toString()) }
        origin?.let { putString("origin", it) }
    }.build()
}

internal fun sentryScopeAdapter(scope: IScope): ITelemetryScope {
    return object : ITelemetryScope {
        override var logLevel: TelemetryLevel?
            get() = scope.level?.toWrapper()
            set(value) {
                scope.level = value?.toSentry()
            }

        override fun addBreadcrumb(breadcrumb: TelemetryBreadcrumb) {
            breadcrumb.log()
            scope.addBreadcrumb(breadcrumb.toSentry())
        }

        override fun clearBreadcrumbs() {
            scope.clearBreadcrumbs()
        }

        override fun clear() {
            scope.clear()
        }

        override fun setTag(key: String, value: String) {
            scope.setTag(key, value)
            Firebase.crashlytics.setCustomKey(key, value)
        }

        override fun removeTag(key: String) {
            scope.removeTag(key)
        }

        override fun setExtra(key: String, value: String) {
            scope.setExtra(key, value)
            Firebase.crashlytics.setCustomKey(key, value)
        }

        override fun removeExtra(key: String) {
            scope.removeExtra(key)
        }
    }
}

object Telemetry : ITelemetry {
    private lateinit var sharedPrefs: SharedPreferences
    private var _enabled: Boolean = false
    private var _test_mode: Boolean = false

    override val isStub: Boolean
        get() = false

    override val enabled: Boolean
        get() = _enabled and !_test_mode

    override fun setEnabled(context: Context, enabled: Boolean) {
        sharedPrefs.edit { putBoolean(PREFS_ENABLED_KEY, enabled) }
        reinit(context, enabled)
    }

    override fun TESTS_ONLY_setTestMode(enabled: Boolean) {
        _test_mode = enabled
    }

    private fun reinit(context: Context, enabled: Boolean) {
        if (_enabled == enabled || _test_mode)
            return
        _enabled = enabled
        Log.i("Telemetry", "Enabled: $enabled")

        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled

        if (!enabled) {
            Sentry.close()
        } else {
            SentryAndroid.init(context) {
                it.dsn = SENTRY_DSN
                it.isEnableUserInteractionTracing = true

                it.environment = if (BuildConfig.DEBUG)
                    "debug"
                else
                    "production"

                it.tracesSampleRate = SAMPLE_RATE
                it.profilesSampleRate = SAMPLE_RATE
                it.sessionReplay.sessionSampleRate = SAMPLE_RATE
                it.sessionReplay.onErrorSampleRate = ERROR_SAMPLE_RATE
            }
        }
    }

    override fun init(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.i("Telemetry", "Enabled: ${sharedPrefs.getBoolean(PREFS_ENABLED_KEY, true)}")
        reinit(context, sharedPrefs.getBoolean(PREFS_ENABLED_KEY, true))
    }

    override fun Modifier.telemetryTag(tag: String): Modifier {
        return if (enabled)
            sentryTag(tag)
        else
            dummyTelemetryTag(tag)

    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun TelemetryTracedImpl(
        tag: String,
        modifier: Modifier,
        enableUserInteractionTracing: Boolean,
        content: @Composable (BoxScope.() -> Unit),
    ) {
        if (!enabled) {
            return DummyTelemetry.TelemetryTracedImpl(
                tag,
                modifier,
                enableUserInteractionTracing,
                content
            )
        }
        SentryTraced(
            tag = tag,
            modifier = modifier,
            enableUserInteractionTracing = enableUserInteractionTracing,
            content = content
        )
    }

    override fun configureScope(callback: ITelemetryScope.() -> Unit) {
        if (!enabled)
            return DummyTelemetry.configureScope(callback)

        Sentry.configureScope {
            sentryScopeAdapter(it).callback()
        }
    }

    override fun captureException(throwable: Throwable): String {
        if (!enabled)
            return DummyTelemetry.captureException(throwable)

        Firebase.crashlytics.recordException(throwable)
        return Sentry.captureException(throwable).toString()
    }

    override fun captureException(
        throwable: Throwable,
        callback: ITelemetryScope.() -> Unit,
    ): String {
        if (!enabled)
            return DummyTelemetry.captureException(throwable, callback)

        val result = Sentry.captureException(throwable) {
            sentryScopeAdapter(it).callback()
        }.toString()
        Firebase.crashlytics.recordException(throwable)
        return result
    }

    override fun addBreadcrumb(breadcrumb: TelemetryBreadcrumb) {
        if (!enabled)
            return DummyTelemetry.addBreadcrumb(breadcrumb)

        breadcrumb.log()
        Firebase.crashlytics.setCustomKeys(breadcrumb.toFirebase())
        Sentry.addBreadcrumb(breadcrumb.toSentry())
    }

    override fun addBreadcrumb(message: String) {
        if (!enabled)
            return DummyTelemetry.addBreadcrumb(message)

        Firebase.crashlytics.log(message)
        addBreadcrumb(TelemetryBreadcrumb.info(message))
    }

    override fun addBreadcrumb(message: String, category: String) {
        if (!enabled)
            return DummyTelemetry.addBreadcrumb(message, category)

        Firebase.crashlytics.log("$category: $message")
        addBreadcrumb(TelemetryBreadcrumb.info(message, category))
    }

    override fun addBreadcrumb(scope: TelemetryBreadcrumb.() -> Unit) {
        if (!enabled)
            return DummyTelemetry.addBreadcrumb(scope)

        val breadcrumb = TelemetryBreadcrumb()
        breadcrumb.scope()
        addBreadcrumb(breadcrumb)
    }

    override fun captureMessage(message: String): String {
        if (!enabled)
            return DummyTelemetry.captureMessage(message)

        TelemetryBreadcrumb.error(message).log()
        Firebase.crashlytics.log(message)
        return Sentry.captureMessage(message).toString()
    }

    override fun captureMessage(
        message: String,
        callback: ITelemetryScope.() -> Unit,
    ): String {
        if (!enabled)
            return DummyTelemetry.captureMessage(message, callback)

        TelemetryBreadcrumb.error(message).log()
        Firebase.crashlytics.log(message)
        return Sentry.captureMessage(message) {
            sentryScopeAdapter(it).callback()
        }.toString()
    }

    override fun captureMessage(
        message: String,
        level: TelemetryLevel,
    ): String {
        if (!enabled)
            return DummyTelemetry.captureMessage(message, level)

        TelemetryBreadcrumb(level = level, message = message).log()
        Firebase.crashlytics.log(message)
        return Sentry.captureMessage(message, level.toSentry()).toString()
    }

    override fun captureMessage(
        message: String,
        level: TelemetryLevel,
        callback: ITelemetryScope.() -> Unit,
    ): String {
        if (!enabled)
            return DummyTelemetry.captureMessage(message, level, callback)

        TelemetryBreadcrumb(level = level, message = message).log()
        Firebase.crashlytics.log(message)
        return Sentry.captureMessage(message, level.toSentry()) {
            sentryScopeAdapter(it).callback()
        }.toString()
    }
}
