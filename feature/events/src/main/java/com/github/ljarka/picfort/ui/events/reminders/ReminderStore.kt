package com.github.ljarka.picfort.ui.events.reminders

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val PREFS = "event_reminders"
private const val KEY_PLAN = "plan"
private const val KEY_CHOICES = "choices"
private const val KEY_DELIVERED = "delivered"
private const val KEY_ASKED_PERMISSION = "asked_permission"
private const val NONE = "none"

/**
 * This phone's reminders (T-039): each parent decides for themselves, so nothing goes to the server.
 * Plain preferences, readable from the alarm and boot receivers without Hilt.
 */
@Singleton
class ReminderStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val choicesSerializer = MapSerializer(Long.serializer(), String.serializer())
    private val planSerializer = ListSerializer(PlannedReminder.serializer())
    private val deliveredSerializer = SetSerializer(String.serializer())

    private val _defaults = MutableStateFlow(readDefaults())
    val defaults: StateFlow<ReminderDefaults> = _defaults.asStateFlow()

    private val _choices = MutableStateFlow(readChoices())
    val choices: StateFlow<Map<Long, ReminderChoice>> = _choices.asStateFlow()

    fun setDefault(kind: ReminderKind, preset: ReminderPreset?) {
        prefs.edit { putString("default_${kind.name}", preset?.name ?: NONE) }
        _defaults.value = _defaults.value.with(kind, preset)
    }

    fun setChoice(eventId: Long, choice: ReminderChoice) {
        val next = _choices.value.toMutableMap().apply {
            if (choice == ReminderChoice.Default) remove(eventId) else put(eventId, choice)
        }
        saveChoices(next)
    }

    /** Drops choices for events that are gone (deleted, or over and not repeating). */
    fun keepChoicesFor(eventIds: Set<Long>) {
        val kept = _choices.value.filterKeys { it in eventIds }
        if (kept.size != _choices.value.size) saveChoices(kept)
    }

    var plan: List<PlannedReminder>
        get() = prefs.getString(KEY_PLAN, null)
            ?.let { runCatching { json.decodeFromString(planSerializer, it) }.getOrNull() }
            .orEmpty()
        set(value) = prefs.edit { putString(KEY_PLAN, json.encodeToString(planSerializer, value)) }

    /** Keys ([PlannedReminder.key]) of reminders already shown, so a re-plan doesn't show them again. */
    val delivered: Set<String>
        get() = prefs.getString(KEY_DELIVERED, null)
            ?.let { runCatching { json.decodeFromString(deliveredSerializer, it) }.getOrNull() }
            .orEmpty()

    fun markDelivered(key: String) {
        // Only the latest ones matter: a key's occurrence is over within days.
        val next = (delivered + key).toList().takeLast(200).toSet()
        prefs.edit { putString(KEY_DELIVERED, json.encodeToString(deliveredSerializer, next)) }
    }

    /** The Events tab asks for the notification permission once. */
    var askedPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_PERMISSION, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_PERMISSION, value) }

    private fun readDefaults(): ReminderDefaults {
        val fallback = ReminderDefaults()
        return ReminderKind.entries.fold(fallback) { acc, kind ->
            when (val stored = prefs.getString("default_${kind.name}", null)) {
                null -> acc
                NONE -> acc.with(kind, null)
                else -> acc.with(kind, ReminderPreset.entries.firstOrNull { it.name == stored } ?: fallback.of(kind))
            }
        }
    }

    private fun readChoices(): Map<Long, ReminderChoice> = prefs.getString(KEY_CHOICES, null)
        ?.let { runCatching { json.decodeFromString(choicesSerializer, it) }.getOrNull() }
        .orEmpty()
        .mapValues { ReminderChoice.fromStored(it.value) }

    private fun saveChoices(choices: Map<Long, ReminderChoice>) {
        val stored = choices.mapNotNull { (id, c) -> c.stored()?.let { id to it } }.toMap()
        prefs.edit { putString(KEY_CHOICES, json.encodeToString(choicesSerializer, stored)) }
        _choices.value = choices
    }
}
