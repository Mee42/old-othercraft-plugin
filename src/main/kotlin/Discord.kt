package org.othercraft

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.GuildMessageChannel
import discord4j.core.`object`.util.Snowflake
import reactor.core.publisher.Mono
import java.io.File

private object Store {
    val isDebug = File("DEBUG").exists()


    val logChannel: Snowflake = when {
        isDebug -> "600755369110667294"
        else -> "600120188309864448"
    }.let { Snowflake.of(it) }

    val statusChannel: Snowflake = when {
        isDebug -> "600755754743496819"
        else -> "600841390439661639"
    }.let { Snowflake.of(it) }

}

class Discord {

    private val key = listOf("key.txt", "/srv/mc/othercraft/key.txt")
        .map { File(it) }.firstOrNull { it.exists() } ?: error("Can't find key")

    val client: DiscordClient = DiscordClientBuilder(key.readText().trim()).build()

    val logChannel: Mono<GuildMessageChannel> by lazy {
        client
            .getChannelById(Store.logChannel)
            .cast(GuildMessageChannel::class.java)
            .cache()
    }

    val statusChannel: Mono<GuildMessageChannel> by lazy {
        client
            .getChannelById(Store.statusChannel)
            .cast(GuildMessageChannel::class.java)
            .cache()
    }



    fun login(){
        client.login().subscribe()
    }


    fun log(message :String) {
        logChannel.flatMap { c ->
            c.createMessage(message)
        }.subscribe()
    }
}