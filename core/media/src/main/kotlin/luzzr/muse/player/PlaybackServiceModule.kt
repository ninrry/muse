package luzzr.muse.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import luzzr.muse.media.PlaybackServiceStarter

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackServiceModule {
    @Binds
    abstract fun bindPlaybackServiceStarter(impl: AndroidPlaybackServiceStarter): PlaybackServiceStarter
}
