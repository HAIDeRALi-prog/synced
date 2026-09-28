package com.example.synced.feature.content.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PostEntity::class, CommentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SyncedDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
}
