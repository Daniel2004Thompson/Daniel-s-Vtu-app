package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        UserProfileEntity::class,
        TransactionEntity::class,
        BeneficiaryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun transactionDao(): TransactionDao
    abstract fun beneficiaryDao(): BeneficiaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val USER_DATABASES = ConcurrentHashMap<String, AppDatabase>()

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daniel_vtu_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Returns a dedicated SQLite Room database instance isolated to a specific user's ID,
         * or falls back to the main database if userId is blank.
         */
        fun getDatabaseForUser(context: Context, userId: String?): AppDatabase {
            val cleanUid = userId?.trim()?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: ""
            if (cleanUid.isBlank() || cleanUid == "usr_guest" || cleanUid == "usr_default") {
                return getDatabase(context)
            }
            return USER_DATABASES.getOrPut(cleanUid) {
                synchronized(this) {
                    USER_DATABASES[cleanUid] ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "daniel_vtu_user_${cleanUid}.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { USER_DATABASES[cleanUid] = it }
                }
            }
        }

        fun deleteDatabaseForUser(context: Context, userId: String?) {
            val cleanUid = userId?.trim()?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: ""
            if (cleanUid.isBlank() || cleanUid == "usr_guest" || cleanUid == "usr_default") return
            synchronized(this) {
                USER_DATABASES.remove(cleanUid)?.close()
                try {
                    context.applicationContext.deleteDatabase("daniel_vtu_user_${cleanUid}.db")
                } catch (_: Throwable) {}
            }
        }
    }
}

