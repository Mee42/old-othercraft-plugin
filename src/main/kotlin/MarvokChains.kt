package org.othercraft

import discord4j.core.`object`.entity.GuildMessageChannel
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.MessageChannel
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.spec.EmbedCreateSpec
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import reactor.core.publisher.Mono
import reactor.util.function.*
import java.awt.Color
import java.time.Instant

fun marvok() {
    Database.init("database.db")
    val processing = yes.discord.client.eventDispatcher.on(MessageCreateEvent::class.java)
        .map { it.message }
        .filter { it.content.isPresent }
        .filter { it.author.isPresent }
        .flatMap { log(it).thenReturn(it) }
        .flatMap {
            it.guild.map { guild -> guild.id }
                .map { guildID ->
                    Tuples.of(it, it.content.get(),it.author.get().id, it.channelId, guildID, it.id.timestamp.epochSecond)
                }
        }
        .flatMap { (mes,content,a,c,g,t) ->
            runCommand(mes, content, a, c, g, t)
                .onErrorResume { throwable -> mes.channel.flatMap { channel -> channel.createEmbed {
                    it.setTitle("EXCEPTION")
                    it.setDescription(throwable.javaClass.simpleName + ":" + throwable.message)
                    it.setColor(Color.RED)
                } }.then() }
        }.onErrorContinue { throwable, _ -> throwable.printStackTrace() }

    val stars = yes.discord.client.eventDispatcher.on(ReactionAddEvent::class.java)
        .filter { it.emoji.asUnicodeEmoji().isPresent }
        .filter { it.emoji.asUnicodeEmoji().get().raw == "⭐"}
        .filterWhen { it.message.map { m -> m.channelId != Snowflake.of(662788144898244608) }}
        .doOnNext { println("Got star!") }
        .doOnNext {
            // insert into database if it doesn't exist yet
            Database.use {
                try {
                    execute("INSERT INTO stars (id) VALUES (${it.messageId.asString()})")
                } catch (e: UnableToExecuteStatementException) {
                    kotlin.io.println("error: " + e.message)
                }
            }
        }
        .flatMap { it.message }
        .flatMap { message ->
            val count = message.reactions.firstOrNull { it.emoji.asUnicodeEmoji().get().raw == "⭐" }?.count ?: 0
            println("count: $count")
            val starPost = Database.with {
                createQuery("SELECT starPost FROM stars WHERE id = ${message.id.asString()}")
                    .mapTo<Long>()
                    .firstOrNull()
            }
            val spec: (EmbedCreateSpec) -> Unit = { it: EmbedCreateSpec ->
                it.setAuthor("$count ⭐'s   Author: " + message.author.get().username, null, message.author.get().avatarUrl)
                it.setDescription(message.content.get().sanitise())
                val url = "https://discordapp.com/channels/513794132871872512/" + message.channelId.asString() + "/" + message.id.asString()
                it.addField("Original Message:", "[jump]($url)", true)
            }
            if(starPost == null && count >= 3){
                // needs to be added to the starboard
                message.client.getChannelById(Snowflake.of(662788144898244608))
                    .cast(MessageChannel::class.java)
                    .flatMap { channel -> channel.createEmbed(spec) }
                    .doOnNext {
                        Database.use {
                            execute("UPDATE stars SET starPost = ${it.id.asString()} WHERE id = ${message.id.asString()}")
                        }
                    }
            } else if(starPost != null) {
                // update the star post anyway
                message.client.getMessageById(Snowflake.of(662788144898244608), Snowflake.of(starPost))
                    .flatMap { it.edit { edit -> edit.setEmbed(spec) } }
            } else {
                Mono.just(Unit)
            }
        }.onErrorContinue { throwable, _ -> throwable.printStackTrace() }
    Mono.first(processing.last(), stars.last()).subscribe()

}


fun String.sanitise(): String {
    return this.replace("@", "@ ")
}

fun log(message: Message): Mono<Void> {
    if(message.channelId == Snowflake.of("600120188309864448")) return Mono.empty()
    return message.guild.map { guildID -> Database.use { record(message, guildID.id) } }.then()
}
fun runCommand(inputMessage: Message, content: String, author: Snowflake, channel: Snowflake, guild: Snowflake, timestamp: Long): Mono<Void> {
    if(content == "!scrape-guild" && author == Snowflake.of(293853365891235841)) {
        // scrap the entire guild
        return yes.discord.client.getGuildById(guild)
            .flatMapMany { it.channels }
            .ofType(GuildMessageChannel::class.java)
            .flatMap { c -> c.getMessagesBefore(Snowflake.of(Instant.now())) }
            .flatMap { message -> message.guild.map { g -> g to message } }
            .buffer(1000)
            .doOnNext { messages ->
                Database.use {
                    for (message in messages) {
                        record(message.second, message.first.id)
                    }
                }
            }.last().flatMap {
                yes.discord.client.getChannelById(channel).cast(MessageChannel::class.java).flatMap { c ->
                    c.createMessage("Done!")
                }
            }.then()

    } else if(content == "!messages"){
        return yes.discord.client.getChannelById(channel).cast(MessageChannel::class.java).flatMap {
                c -> c.createMessage("messages:" + Database.with {
            createQuery("SELECT COUNT(*) FROM messages")
                .mapTo<Int>()
                .first()
        })
        }.then()
    } else if(content.startsWith("!chain")){
        return chain(inputMessage, content.substring("!chain".length).trim())
    }
    return Mono.empty()
}


fun Handle.record(message: Message, guildID: Snowflake) {
    if(!message.content.isPresent) return
    if(!message.author.isPresent) return
    val content = message.content.get()
    val author = message.author.get().id
    val channel = message.channelId
    val timestamp = message.timestamp.epochSecond
    try {
        createUpdate(
            "INSERT INTO messages (content, author, channel, guild, timestamp) VALUES (:content,:author,:channel,:guild,:timestamp)")
            .bind("content", content)
            .bind("author", author.asLong())
            .bind("channel", channel.asLong())
            .bind("guild", guildID.asLong())
            .bind("timestamp", timestamp)
            .execute()
    }catch(e: UnableToExecuteStatementException){
        kotlin.io.println("error: " + e.message)
    }

    kotlin.io.println(
        java.util.Date.from(java.time.Instant.ofEpochSecond(timestamp)).toString() + " ${guildID.asString()}:${channel.asString()}:${author.asString()}   " +
                content.replace("\n", "\\n")
    )
}


operator fun <T1,T2> Tuple2<T1, T2>.component1():T1 = t1
operator fun <T1,T2> Tuple2<T1, T2>.component2():T2 = t2
operator fun <T1,T2,T3> Tuple3<T1, T2, T3>.component3():T3 = t3
operator fun <T1,T2,T3,T4> Tuple4<T1, T2, T3, T4>.component4():T4 = t4
operator fun <T1,T2,T3,T4,T5> Tuple5<T1, T2, T3, T4, T5>.component5():T5 = t5