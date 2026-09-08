package com.oursician.app.tablature.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SongLibraryUiState(val songs: List<Song> = emptyList(), val errorMessage: String? = null)

class SongLibraryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SongRepository = SongRepository(application),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SongLibraryUiState(songs = repository.listSongs()))
    val uiState: StateFlow<SongLibraryUiState> = _uiState.asStateFlow()

    fun importSong(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.importSong(uri) } }
            result.fold(
                onSuccess = { _uiState.update { it.copy(songs = repository.listSongs(), errorMessage = null) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Impossible d'importer ce fichier : ${error.message}")
                    }
                },
            )
        }
    }

    fun deleteSong(song: Song) {
        repository.deleteSong(song)
        _uiState.update { it.copy(songs = repository.listSongs()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
