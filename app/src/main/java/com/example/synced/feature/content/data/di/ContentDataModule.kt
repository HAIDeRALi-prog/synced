package com.example.synced.feature.content.data.di

import androidx.room.Room
import com.example.synced.feature.content.data.ContentRepositoryImpl
import com.example.synced.feature.content.data.local.CommentDao
import com.example.synced.feature.content.data.local.PostDao
import com.example.synced.feature.content.data.local.SyncedDatabase
import com.example.synced.feature.content.data.remote.ContentApi
import com.example.synced.feature.content.domain.ContentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContentDataModule {

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    companion object {
        @Provides
        @Singleton
        fun provideContentApi(retrofit: Retrofit): ContentApi =
            retrofit.create(ContentApi::class.java)

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SyncedDatabase =
            Room.databaseBuilder(context, SyncedDatabase::class.java, "synced.db").build()

        @Provides
        fun providePostDao(db: SyncedDatabase): PostDao = db.postDao()

        @Provides
        fun provideCommentDao(db: SyncedDatabase): CommentDao = db.commentDao()
    }
}
