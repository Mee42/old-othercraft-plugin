package org.othercraft

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import java.io.File
import java.lang.RuntimeException

object Database {
    private lateinit var jdbi: Jdbi
    fun init(dbFile: String){
        jdbi = Jdbi.create("jdbc:sqlite:$dbFile")
        if(!File(dbFile).exists()){
            use {
                //language=SQLite
                execute("""
                    CREATE TABLE messages (
                        content TEXT,
                        author INTEGER NOT NULL,
                        channel INTEGER NOT NULL,
                        guild INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        PRIMARY KEY(author,channel,guild,timestamp)
                    )
                """.trimIndent())
                //language=SQLite
                execute("""
                    CREATE TABLE stars (
                        id INTEGER NOT NULL,
                        starPost INTEGER NULL,
                        PRIMARY KEY (id)
                    )
                """.trimIndent())
            }
        }
    }
    fun use(block: Handle.() -> Unit){
        jdbi.useHandle<RuntimeException>(block)
    }
    fun <R> with(block: Handle.() -> R) :R {
        return jdbi.withHandle<R,RuntimeException>(block)
    }
}