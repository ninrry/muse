package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.GreetingPeriod
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetGreetingUseCase @Inject constructor() {
    operator fun invoke(): GreetingPeriod {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> GreetingPeriod.MORNING
            in 12..17 -> GreetingPeriod.AFTERNOON
            else -> GreetingPeriod.EVENING
        }
    }
}
