package eu.kanade.tachiyomi.di

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DatabaseUsabilityGuardTest {

    @Test
    fun `valid database with required table is kept`() {
        classifyDatabaseUsability(
            hasRequiredTable = true,
            userVersion = 10,
        ) shouldBe DatabaseUsabilityOutcome.Usable
    }

    @Test
    fun `fresh uninitialized database with userVersion 0 is kept for SQLDelight creation`() {
        classifyDatabaseUsability(
            hasRequiredTable = false,
            userVersion = 0,
        ) shouldBe DatabaseUsabilityOutcome.Usable
    }

    @Test
    fun `partially migrated database with non-zero userVersion and missing required table is recreated`() {
        classifyDatabaseUsability(
            hasRequiredTable = false,
            userVersion = 9,
        ) shouldBe DatabaseUsabilityOutcome.RecreateMissingRequiredTable
    }

    @Test
    fun `transient open or lock errors never delete user databases`() {
        classifyDatabaseInspectionFailure(
            SQLiteCantOpenDatabaseException("unable to open database file (code 14)"),
        ) shouldBe DatabaseUsabilityOutcome.KeepOnTransientError

        classifyDatabaseInspectionFailure(
            SQLiteDatabaseLockedException("database is locked (code 5)"),
        ) shouldBe DatabaseUsabilityOutcome.KeepOnTransientError

        classifyDatabaseInspectionFailure(
            SQLiteReadOnlyDatabaseException("attempt to write a readonly database"),
        ) shouldBe DatabaseUsabilityOutcome.KeepOnTransientError
    }

    @Test
    fun `explicit sqlite corruption triggers safe recreation`() {
        classifyDatabaseInspectionFailure(
            SQLiteNotADatabaseException("file is not a database"),
        ) shouldBe DatabaseUsabilityOutcome.RecreateCorrupted

        classifyDatabaseInspectionFailure(
            RuntimeException("database disk image is malformed (code 11)"),
        ) shouldBe DatabaseUsabilityOutcome.RecreateCorrupted

        classifyDatabaseInspectionFailure(
            IllegalStateException("wrapper", RuntimeException("file is not a database")),
        ) shouldBe DatabaseUsabilityOutcome.RecreateCorrupted
    }

    private class SQLiteCantOpenDatabaseException(message: String) : RuntimeException(message)

    private class SQLiteDatabaseLockedException(message: String) : RuntimeException(message)

    private class SQLiteReadOnlyDatabaseException(message: String) : RuntimeException(message)

    private class SQLiteNotADatabaseException(message: String) : RuntimeException(message)
}
