package org.othercraft

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.util.Snowflake
import org.jdbi.v3.core.kotlin.mapTo
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.util.*


data class Chain(val map: MutableMap<Pair<String,String>,MutableList<Pair<String,Int>>> = mutableMapOf())

enum class ChainType {
    GLOBAL, CHANNEL, GUILD, USER
}

const val INITIAL_0 = "!!!INITIAL_0!!!"
const val INITIAL_1 = "!!!INITIAL_1!!!"
const val END_YES   = "!!!END!!!"

object Chains {

    val global = loadFromDB(ChainType.GLOBAL, null)

    private val users = mutableMapOf<Snowflake, Chain>()
    private val channel = mutableMapOf<Snowflake, Chain>()
    private val guild = mutableMapOf<Snowflake, Chain>()

    fun userChain(id: Snowflake): Chain {
        return users.computeIfAbsent(id) { loadFromDB(ChainType.USER, id) }
    }
    fun channelChain(id: Snowflake): Chain {
        return channel.computeIfAbsent(id) { loadFromDB(ChainType.CHANNEL, id) }
    }
    fun guildChain(id: Snowflake): Chain {
        return guild.computeIfAbsent(id) { loadFromDB(ChainType.GUILD, id) }
    }



    // id == null ONLY when type == GLOBAL
    private fun loadFromDB(type: ChainType, id: Snowflake?): Chain {
        val sql = "SELECT content FROM messages" + when(type) {
            ChainType.GLOBAL -> ""
            ChainType.CHANNEL -> " WHERE channel = " + id!!.asString()
            ChainType.GUILD -> " WHERE guild = " + id!!.asString()
            ChainType.USER -> " WHERE author = " + id!!.asString()
        }
        val messages: List<String> = Database.with {
            createQuery(sql).mapTo<String>().list()
        }
        val chain = Chain()
        messages.forEach { content ->
            val words = content.split(Regex("""\s+""")).map { it.fixUp() }
            if(words.isEmpty()) return@forEach
            chain.incr(INITIAL_0 to INITIAL_1, words[0])
            if(words.size == 1) return@forEach
            chain.incr(INITIAL_1 to words[0], words[1])
            for(i in 2 until words.size){
                chain.incr(words[i - 2] to words[i - 1], words[i])
            }
            chain.incr(words[words.size - 2] to words[words.size - 1], END_YES)
        }
        return chain
    }
    private fun Chain.incr(pair: Pair<String,String>, end: String) {
        val list= this.map.getOrPut(pair) { mutableListOf() }
        when (val indexOfElem = list.indexOfFirst { it.first == end }) {
            -1 -> list += end to 1
            else -> list[indexOfElem] = end to list[indexOfElem].second + 1
        }
    }
}

private fun String.fixUp(): String {
    return this.replace("~~","")
}

var debug = false

fun chain(message: Message, arguments: String): Mono<Void> {
    if(arguments == "dev.mee42.getDebug" && message.author.get().id == Snowflake.of(293853365891235841)){
        debug = !debug
        return message.channel.flatMap { it.createMessage(":+1:" + if(debug) " on" else " off") }.then()
    }
    if(arguments == "help") {
        return message.channel.flatMap { channel ->
            val help = mapOf("global" to "all the messages the bot has",
                  "guild" to "all the messages in this guild (server)",
                  "me" to "all the messages sent by me",
                  "u[id]" to "all the messages sent by the user with id [id]",
                  "c[id]" to "all the messages sent in the channel with an id of [id]",
                  "g[id]" to "all the messages sent in the guild (server) with an id of [id]",
                  "@mention" to "all messages sent by ONE of the people mentioned. If you mention 2 people, it'll pick on of them.",
                  "#channelMention" to "all messages sent in that channel")
            channel.createEmbed {
                it.setTitle("Help menu")
                it.setDescription("[id] means a discord snowflake. For example, `600128289683668993`")
                help.forEach { (name, desc) ->
                    it.addField("!.chain $name", desc, false)
                }
            }
        }.then()
    }
    val chain = if(arguments == "guild") {
        message.guild.map { "guild:${it.id}" to Chains.guildChain(it.id) }
    } else when {
        arguments.isEmpty() || arguments == "global" -> "global" to Chains.global
        arguments == "me" -> "user:${message.author.get().id.asString()}" to Chains.userChain(message.author.get().id)
        arguments.startsWith("u") -> {
            val id = arguments.substring(1).trim()
            "user:$id" to Chains.userChain(Snowflake.of(id))
        }
        arguments.startsWith("c") -> {
            val id = arguments.substring(1).trim()
            "channel:$id" to Chains.channelChain(Snowflake.of(id))
        }

        message.userMentionIds.isNotEmpty() -> {
            "user:${message.userMentionIds.first().asString()}" to Chains.userChain(message.userMentionIds.first())
        }
        arguments.startsWith("<#") -> {
            val channelID = arguments.substring(2).replace(">","").trim()
            "channel:$channelID" to Chains.channelChain(Snowflake.of(channelID))
        }
        else -> return message.channel.flatMap { it.createMessage("Can't parse `${arguments.sanitise()}` as !.chain arguments") }.then()
    }.toMono()
    // for right now, assume global
    return message.channel.zipWith(chain).flatMap { (channel, chain_) ->
        channel.createMessage((if(debug) "`" + chain_.first + "` > " else "> ") + chain_.second.genSentence().sanitise())
    }.then()
}


fun Chain.genSentence(): String {
    val sentence = mutableListOf(INITIAL_0, INITIAL_1)
    while(sentence.last() != END_YES && sentence.sumBy { it.length } < 1000){
        val options = this.map[sentence[sentence.size - 2] to sentence.last()]
        // pick a random one based on probability
        if(options == null){
            sentence.add("NULL")
            sentence.add(END_YES)
            continue
        }
        val next = options.flatMap { listOfDuplicates(it.second, it.first) }.random()
        sentence.add(next)
    }
    val dotdotdot = sentence.last() != END_YES
    sentence.removeIf { it in listOf(INITIAL_0, INITIAL_1, END_YES) }
    return sentence.fold("") { str, elem -> "$str $elem" }.trim() + if(dotdotdot) "..." else ""
}

inline fun <reified T> listOfDuplicates(count: Int, elem: T): List<T> = Array(count) { elem }.toList()
