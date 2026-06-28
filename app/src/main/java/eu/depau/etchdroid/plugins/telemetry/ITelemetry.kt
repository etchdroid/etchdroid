package eu.depau.etchdroid.plugins.telemetry

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier

interface ITelemetry {
    val isStub: Boolean
    val enabled: Boolean get() = !isStub

    fun setEnabled(context: Context, enabled: Boolean) {}

    @Suppress("FunctionName")
    fun TESTS_ONLY_setTestMode(enabled: Boolean) {}

    fun init(context: Context) {}

    fun Modifier.telemetryTag(tag: String): Modifier

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun TelemetryTracedImpl(
        tag: String,
        modifier: Modifier,
        enableUserInteractionTracing: Boolean,
        content: @Composable BoxScope.() -> Unit,
    )

    /**
     * Configures the scope through the callback.
     *
     * @param callback The configure scope callback.
     */
    fun configureScope(callback: ITelemetryScope.() -> Unit);

    /**
     * Captures the exception.
     *
     * @param throwable The exception.
     * @return The Id (SentryId object) of the event
     */
    fun captureException(throwable: Throwable): String


    /**
     * Captures the exception.
     *
     * @param throwable The exception.
     * @return The Id (SentryId object) of the event
     */
    fun captureException(
        message: String,
        throwable: Throwable,
        callback: ITelemetryScope.() -> Unit = {},
    ): String =
        captureException(throwable) {
            addBreadcrumb(TelemetryBreadcrumb.error(message))
            callback()
        }

    /**
     * Captures the exception.
     *
     * @param throwable The exception.
     * @param callback The callback to configure the scope for a single invocation.
     * @return The Id (SentryId object) of the event
     */
    fun captureException(throwable: Throwable, callback: ITelemetryScope.() -> Unit = {}): String


    /**
     * Adds a breadcrumb to the current Scope
     *
     * @param breadcrumb the breadcrumb
     */
    fun addBreadcrumb(breadcrumb: TelemetryBreadcrumb)

    /**
     * Adds a breadcrumb to the current Scope
     *
     * @param message rendered as text and the whitespace is preserved.
     */
    fun addBreadcrumb(message: String)

    /**
     * Adds a breadcrumb to the current Scope
     *
     * @param message rendered as text and the whitespace is preserved.
     * @param category Categories are dotted strings that indicate what the crumb is or where it comes
     *     from.
     */
    fun addBreadcrumb(message: String, category: String)

    /**
     * Adds a breadcrumb to the current Scope
     *
     * @param scope the callback to configure the breadcrumb
     */
    fun addBreadcrumb(scope: TelemetryBreadcrumb.() -> Unit)


    /**
     * Adds a breadcrumb to the current Scope with the given message and INFO level.
     *
     * @param message rendered as text and the whitespace is preserved.
     * @param scope the callback to further configure the breadcrumb
     */
    fun info(
        message: String,
        category: String? = null,
        scope: TelemetryBreadcrumb.() -> Unit = {},
    ) = addBreadcrumb {
        this.message = message
        this.level = TelemetryLevel.INFO
        if (category != null)
            this.category = category
        scope()
    }

    /**
     * Adds a breadcrumb to the current Scope with the given message and WARNING level.
     *
     * @param message rendered as text and the whitespace is preserved.
     * @param scope the callback to further configure the breadcrumb
     */
    fun warning(
        message: String,
        category: String? = null,
        scope: TelemetryBreadcrumb.() -> Unit = {},
    ) = addBreadcrumb {
        this.message = message
        this.level = TelemetryLevel.WARNING
        if (category != null)
            this.category = category
        scope()
    }

    /**
     * Adds a breadcrumb to the current Scope with the given message and DEBUG level.
     *
     * @param message rendered as text and the whitespace is preserved.
     * @param scope the callback to further configure the breadcrumb
     */
    fun debug(
        message: String,
        category: String? = null,
        scope: TelemetryBreadcrumb.() -> Unit = {},
    ) = addBreadcrumb {
        this.message = message
        this.level = TelemetryLevel.DEBUG
        if (category != null)
            this.category = category
        scope()
    }

    /**
     * Captures the message.
     *
     * @param message The message to send.
     * @return The Id (SentryId object) of the event
     */
    fun captureMessage(message: String): String

    /**
     * Captures the message.
     *
     * @param message The message to send.
     * @param callback The callback to configure the scope for a single invocation.
     * @return The Id (SentryId object) of the event
     */
    fun captureMessage(message: String, callback: ITelemetryScope.() -> Unit = {}): String

    /**
     * Captures the message.
     *
     * @param message The message to send.
     * @param level The message level.
     * @return The Id (SentryId object) of the event
     */
    fun captureMessage(message: String, level: TelemetryLevel): String

    /**
     * Captures the message.
     *
     * @param message The message to send.
     * @param level The message level.
     * @param callback The callback to configure the scope for a single invocation.
     * @return The Id (SentryId object) of the event
     */
    fun captureMessage(
        message: String,
        level: TelemetryLevel,
        callback: ITelemetryScope.() -> Unit = {},
    ): String

}


