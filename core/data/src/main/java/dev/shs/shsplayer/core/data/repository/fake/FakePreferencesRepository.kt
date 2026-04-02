package dev.shs.shsplayer.core.data.repository.fake

import dev.shs.shsplayer.core.data.repository.PreferencesRepository
import dev.shs.shsplayer.core.model.ApplicationPreferences
import dev.shs.shsplayer.core.model.PlayerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakePreferencesRepository : PreferencesRepository {

    private val applicationPreferencesStateFlow = MutableStateFlow(ApplicationPreferences())
    private val playerPreferencesStateFlow = MutableStateFlow(PlayerPreferences())

    override val applicationPreferences: StateFlow<ApplicationPreferences>
        get() = applicationPreferencesStateFlow
    override val playerPreferences: StateFlow<PlayerPreferences>
        get() = playerPreferencesStateFlow

    override suspend fun updateApplicationPreferences(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    ) {
        applicationPreferencesStateFlow.update { transform.invoke(it) }
    }

    override suspend fun updatePlayerPreferences(
        transform: suspend (PlayerPreferences) -> PlayerPreferences,
    ) {
        playerPreferencesStateFlow.update { transform.invoke(it) }
    }

    override suspend fun resetPreferences() {
        applicationPreferencesStateFlow.update { ApplicationPreferences() }
        playerPreferencesStateFlow.update { PlayerPreferences() }
    }
}
