package dao

import charLengthSum
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import tk.skeptick.bot.Message

data class UserStat(val messageCount: Int, val charCount: Int)

data class HistoryMessage(
        val messageId: Int,
        val chatId: Int,
        val userId: Int,
        val text: String,
        val charCount: Int,
        val date: DateTime)

private fun ResultRow.toHistoryMessage(): HistoryMessage {
    return HistoryMessage(
            messageId = this[MessagesHistory.messageId],
            chatId = this[MessagesHistory.chatId],
            userId = this[MessagesHistory.userId],
            text = this[MessagesHistory.text],
            charCount = this[MessagesHistory.text].length,
            date = this[MessagesHistory.date])
}

object MessagesHistory : Table("messages_history") {
    val messageId = integer("message_id").primaryKey().autoIncrement()
    val chatId = integer("chat_id").index()
    val userId = integer("user_id").index()
    val text = text("text")
    val date = datetime("date").index()
}

fun MessagesHistory.add(messageId: Int, chatId: Int, userId: Int, text: String, date: Int) {
    transaction {
        insertIgnore {
            it[this.messageId] = messageId
            it[this.chatId] = chatId
            it[this.userId] = userId
            it[this.text] = text
            it[this.date] = DateTime(date * 1000.toLong())
        }
    }
}

fun MessagesHistory.addAll(messages: List<Message>) {
    transaction {
        batchInsert(messages, true) {
            this[messageId] = it.id
            this[chatId] = it.chatId!!
            this[userId] = it.userId
            this[text] = it.body
            this[date] = DateTime(it.date * 1000.toLong())
        }
    }
}

fun MessagesHistory.getMessagesCountForUsersByChat(
        chatId: Int,
        datetime: DateTime? = null
): Map<Int, UserStat> {

    return transaction {
        if (datetime != null) {
            slice(MessagesHistory.userId, MessagesHistory.userId.count(), MessagesHistory.text.charLengthSum())
                    .select { (MessagesHistory.date greaterEq datetime) and
                            (MessagesHistory.chatId eq chatId) }
                    .groupBy(MessagesHistory.userId)
                    .associate {
                        it[MessagesHistory.userId] to UserStat(
                                messageCount = it[MessagesHistory.userId.count()],
                                charCount = it[MessagesHistory.text.charLengthSum()])
                    }
        } else {
            slice(MessagesHistory.userId, MessagesHistory.userId.count(), MessagesHistory.text.charLengthSum())
                    .select { MessagesHistory.chatId eq chatId }
                    .groupBy(MessagesHistory.userId)
                    .associate {
                        it[MessagesHistory.userId] to UserStat(
                                messageCount = it[MessagesHistory.userId.count()],
                                charCount = it[MessagesHistory.text.charLengthSum()])
                    }
        }
    }
}

fun MessagesHistory.getMessagesCountForUserByChatAndUserId(
        chatId: Int,
        userId: Int,
        datetime: DateTime? = null
): Map<DateTime, UserStat> {
    return transaction {
        if (datetime != null) {
            slice(MessagesHistory.date.date(), MessagesHistory.messageId.count(), MessagesHistory.text.charLengthSum())
                    .select { (MessagesHistory.date greaterEq datetime) and
                            (MessagesHistory.userId eq userId) and
                            (MessagesHistory.chatId eq chatId) }
                    .groupBy(MessagesHistory.date.date())
                    .orderBy(MessagesHistory.date.date())
                    .associate {
                        it[MessagesHistory.date.date()] to UserStat(
                            messageCount = it[MessagesHistory.messageId.count()],
                            charCount = it[MessagesHistory.text.charLengthSum()])
                    }
        } else {
            slice(MessagesHistory.date.date(), MessagesHistory.messageId.count(), MessagesHistory.text.charLengthSum())
                    .select { (MessagesHistory.userId eq userId) and
                            (MessagesHistory.chatId eq chatId) }
                    .groupBy(MessagesHistory.date.date())
                    .orderBy(MessagesHistory.date.date())
                    .associate {
                        it[MessagesHistory.date.date()] to UserStat(
                                messageCount = it[MessagesHistory.messageId.count()],
                                charCount = it[MessagesHistory.text.charLengthSum()])
                    }
        }
    }
}

fun MessagesHistory.getLastMessagesForUsersByChat(
        chatId: Int
): Map<Int, DateTime?> {

    return transaction {
        slice(MessagesHistory.userId, MessagesHistory.date.max())
                .select { MessagesHistory.chatId eq chatId }
                .groupBy(MessagesHistory.userId)
                .associate { it[MessagesHistory.userId] to it[MessagesHistory.date.max()] }
    }
}

fun MessagesHistory.getLastMessageForUserByChatAndUserId(
        chatId: Int,
        userId: Int
): DateTime? {

    return transaction {
        slice(MessagesHistory.userId, MessagesHistory.date.max())
                .select { (MessagesHistory.userId eq userId) and
                    (MessagesHistory.chatId eq chatId) }
                .singleOrNull()
                ?.let { it[MessagesHistory.date.max()] }
    }
}

fun MessagesHistory.getFirstMessageDateByChat(
        chatId: Int,
        datetime: DateTime? = null
): DateTime? {

    return transaction {
        if (datetime != null) {
            slice(MessagesHistory.date.min())
                    .select { (MessagesHistory.date greaterEq datetime) and
                            (MessagesHistory.chatId eq chatId) }
                    .single()
                    .let { it[MessagesHistory.date.min()] }
        } else {
            slice(MessagesHistory.date.min())
                    .select { MessagesHistory.chatId eq chatId }
                    .single()
                    .let { it[MessagesHistory.date.min()] }
        }
    }
}

fun MessagesHistory.getFirstMessageDateByChatAndUser(
        chatId: Int,
        userId: Int,
        datetime: DateTime? = null
): DateTime? {

    return transaction {
        if (datetime != null) {
            slice(MessagesHistory.date.min())
                    .select { (MessagesHistory.date greaterEq datetime) and
                            (MessagesHistory.userId eq userId) and
                            (MessagesHistory.chatId eq chatId) }
                    .singleOrNull()
                    ?.let { it[MessagesHistory.date.min()] }
        } else {
            slice(MessagesHistory.date.min())
                    .select { (MessagesHistory.userId eq userId) and
                        (MessagesHistory.chatId eq chatId) }
                    .singleOrNull()
                    ?.let { it[MessagesHistory.date.min()] }
        }
    }
}

fun MessagesHistory.getUserMessages(
        chatId: Int,
        userId: Int,
        datetime: DateTime? = null
): List<HistoryMessage> {

    return transaction {
        if (datetime != null) {
            select { (MessagesHistory.date greaterEq datetime) and
                    (MessagesHistory.userId eq userId) and
                    (MessagesHistory.chatId eq chatId) }
                    .orderBy(MessagesHistory.date)
                    .map(ResultRow::toHistoryMessage)
        } else {
            select { (MessagesHistory.userId eq userId) and
                    (MessagesHistory.chatId eq chatId) }
                    .orderBy(MessagesHistory.date)
                    .map(ResultRow::toHistoryMessage)
        }
    }
}