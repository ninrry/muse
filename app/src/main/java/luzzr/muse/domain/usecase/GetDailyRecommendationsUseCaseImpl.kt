package luzzr.muse.domain.usecase

import android.app.Application
import androidx.core.content.edit
import luzzr.muse.domain.model.Song
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_DAILY_REC = "daily_recommendation_prefs"
private const val KEY_CYCLE_START = "cycle_start"
private const val KEY_RECOMMENDED_IDS = "recommended_ids"
private const val DAILY_REC_COUNT = 20

@Singleton
class GetDailyRecommendationsUseCaseImpl @Inject constructor(
    private val application: Application
) : GetDailyRecommendationsUseCase {

    override operator fun invoke(allSongs: List<Song>): List<Song> {
        if (allSongs.isEmpty()) return emptyList()

        val prefs = application.getSharedPreferences(PREFS_DAILY_REC, 0)
        val todayStart = run {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        val savedCycleStart = prefs.getLong(KEY_CYCLE_START, 0L)
        val recommendedIdsStr = prefs.getString(KEY_RECOMMENDED_IDS, "") ?: ""

        val isNewCycle = savedCycleStart < todayStart
        val previouslyRecommended: Set<Long> = if (isNewCycle) {
            emptySet()
        } else {
            recommendedIdsStr.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        }

        val available = allSongs.filter { it.id !in previouslyRecommended }

        val toPick = if (available.size <= DAILY_REC_COUNT) {
            val shuffled = allSongs.shuffled()
            prefs.edit {
                putLong(KEY_CYCLE_START, todayStart)
                putString(KEY_RECOMMENDED_IDS, shuffled.take(DAILY_REC_COUNT).joinToString(",") { it.id.toString() })
            }
            return shuffled.take(DAILY_REC_COUNT)
        } else {
            available.shuffled().take(DAILY_REC_COUNT)
        }

        val newRecommended = (previouslyRecommended + toPick.map { it.id })
        prefs.edit {
            putLong(KEY_CYCLE_START, if (isNewCycle) todayStart else savedCycleStart)
            putString(KEY_RECOMMENDED_IDS, newRecommended.joinToString(","))
        }

        return toPick
    }
}
