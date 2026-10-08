package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        UserProfileEntity::class,
        TransactionEntity::class,
        BeneficiaryEntity::class
    ],
    version = 4,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profiles_new` (
                        `userId` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL,
                        `phone` TEXT,
                        `nin` TEXT,
                        `ninHash` TEXT,
                        `ninVerified` INTEGER NOT NULL,
                        `walletBalance` REAL NOT NULL,
                        `cashbackBalance` REAL NOT NULL,
                        `virtualAccountNumber` TEXT,
                        `virtualBankName` TEXT,
                        `virtualAccountName` TEXT,
                        `dynamicAccountNumber` TEXT,
                        `dynamicBankName` TEXT,
                        `dynamicAccountName` TEXT,
                        `dynamicAccountAmount` REAL,
                        `biometricEnabled` INTEGER NOT NULL,
                        `appLockEnabled` INTEGER NOT NULL,
                        `notificationsEnabled` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `user_profiles_new` (
                        `userId`, `email`, `fullName`, `phone`, `nin`, `ninHash`, `ninVerified`,
                        `walletBalance`, `cashbackBalance`, `virtualAccountNumber`, `virtualBankName`,
                        `virtualAccountName`, `dynamicAccountNumber`, `dynamicBankName`, `dynamicAccountName`,
                        `dynamicAccountAmount`, `biometricEnabled`, `appLockEnabled`, `notificationsEnabled`, `updatedAt`
                    )
                    SELECT
                        `userId`, `email`, `fullName`, `phone`, `nin`, `ninHash`, `ninVerified`,
                        `walletBalance`, `cashbackBalance`, `virtualAccountNumber`, `virtualBankName`,
                        `virtualAccountName`, `dynamicAccountNumber`, `dynamicBankName`, `dynamicAccountName`,
                        `dynamicAccountAmount`, `biometricEnabled`, `appLockEnabled`, `notificationsEnabled`, `updatedAt`
                    FROM `user_profiles`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `user_profiles`")
                db.execSQL("ALTER TABLE `user_profiles_new` RENAME TO `user_profiles`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_email` ON `user_profiles` (`email`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `createdAt` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daniel_vtu_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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

