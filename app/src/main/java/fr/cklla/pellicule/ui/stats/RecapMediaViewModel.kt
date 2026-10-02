package fr.cklla.pellicule.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.cklla.pellicule.domain.model.Media
import fr.cklla.pellicule.domain.model.MediaType
import fr.cklla.pellicule.domain.repository.MediaRepository
import fr.cklla.pellicule.ui.bibliotheque.BibliothequeFilter
import fr.cklla.pellicule.ui.bibliotheque.filterMedia
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Contenus vus d'une année, ouverts depuis l'écran du récap en tapant le total ou le compteur d'un
 * type. [year] et [type] viennent de la route ; le filtrage est celui de la Bibliothèque
 * ([filterMedia] sous Vu), donc identique à son onglet Vu filtré sur cette année et ce type.
 */
@HiltViewModel
class RecapMediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mediaRepository: MediaRepository,
) : ViewModel() {

    val year: Int = checkNotNull(savedStateHandle[PelliculeDestinations.RECAP_ARG_YEAR])

    /** `null` = tous les types. */
    val type: MediaType? = parseRecapType(savedStateHandle[PelliculeDestinations.RECAP_ARG_TYPE])

    val media: StateFlow<List<Media>> = mediaRepository.observeMedia()
        .map { media -> filterMedia(media, BibliothequeFilter.VU, year, type) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}

/** Type porté par la route du récap : `null` pour « tous » ou pour toute valeur inconnue. */
internal fun parseRecapType(value: String?): MediaType? =
    MediaType.entries.firstOrNull { it.name == value }
