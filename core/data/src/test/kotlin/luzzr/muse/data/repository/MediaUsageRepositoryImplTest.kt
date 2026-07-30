package luzzr.muse.data.repository

import io.mockk.every
import io.mockk.mockk
import luzzr.muse.data.database.MediaUsageDao
import luzzr.muse.data.database.MediaUsageEntity
import luzzr.muse.domain.model.MediaUsageType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class MediaUsageRepositoryImplTest {
    @Test
    fun `audiobook rows produce non-zero reading and listening statistics`() = runTest {
        val dao = mockk<MediaUsageDao>()
        val today = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        every { dao.observeForType(MediaUsageType.MUSIC.name) } returns flowOf(emptyList())
        every { dao.observeForType(MediaUsageType.AUDIOBOOK.name) } returns flowOf(
            listOf(
                MediaUsageEntity(
                    mediaType = MediaUsageType.AUDIOBOOK.name,
                    mediaId = "book-1",
                    dayStart = today,
                    listenedMs = 75_000L,
                    readMs = 90_000L
                )
            )
        )

        val stats = MediaUsageRepositoryImpl(dao).observeReadAlongStats().first()

        assertEquals(90_000L, stats.totalReadMs)
        assertEquals(90_000L, stats.todayReadMs)
        assertEquals(90_000L, stats.weekReadMs)
        assertEquals(75_000L, stats.totalListenedMs)
        assertEquals(75_000L, stats.todayListenedMs)
        assertEquals(75_000L, stats.weekListenedMs)
    }
}
