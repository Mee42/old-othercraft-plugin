package org.othercraft

import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.GuildMessageChannel
import discord4j.core.`object`.util.Snowflake
import reactor.core.publisher.Mono
import java.io.File

class Discord {

    private val key = listOf("key.txt", "/srv/mc/othercraft/key.txt")
        .map { File(it) }.firstOrNull { it.exists() } ?: error("Can't find key")

    val client: DiscordClient = DiscordClientBuilder(key.readText().trim()).build()

    val logChannel: Mono<GuildMessageChannel> by lazy {
        client
            .getChannelById(Snowflake.of("600120188309864448"))
            .cast(GuildMessageChannel::class.java)
            .cache()
    }

    val statusChannel: Mono<GuildMessageChannel> by lazy {
        client
            .getChannelById(Snowflake.of("600320275904724995"))
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