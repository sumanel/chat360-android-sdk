package com.chat360.chatbot.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_conversations", indices = [Index(value = ["botId", "roomId"])])
data class CachedConversationEntity(
    @PrimaryKey val id: String,
    val botId: String,
    val roomId: String? = null,
    val title: String = "New conversation",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = CachedConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId")],
)
data class CachedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    /** RAW stores an incoming websocket envelope; USER stores a locally-authored display message. */
    val kind: String,
    val payload: String,
    val chatMsgId: String? = null,
    val createdAt: Long,
)

@Dao
interface ChatCacheDao {
    @Query("SELECT * FROM chat_conversations WHERE botId = :botId AND roomId = :roomId LIMIT 1")
    suspend fun findConversation(botId: String, roomId: String): CachedConversationEntity?

    @Query("SELECT * FROM chat_conversations WHERE botId = :botId ORDER BY updatedAt DESC")
    fun observeConversations(botId: String): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun messages(conversationId: String): List<CachedMessageEntity>

    @Query("SELECT id FROM chat_conversations WHERE botId = :botId AND id LIKE 'dealer-room:%'")
    suspend fun dealerRoomConversationIds(botId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: CachedConversationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversationIfMissing(conversation: CachedConversationEntity)

    @Query("UPDATE chat_conversations SET roomId = :roomId, title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateRemoteConversation(conversationId: String, roomId: String, title: String, updatedAt: Long)

    @Transaction
    suspend fun replaceDealerRoomConversations(botId: String, conversations: List<CachedConversationEntity>) {
        val refreshedIds = conversations.mapTo(mutableSetOf()) { it.id }
        dealerRoomConversationIds(botId)
            .filterNot(refreshedIds::contains)
            .forEach { deleteConversation(it) }
        conversations.forEach { conversation ->
            insertConversationIfMissing(conversation)
            updateRemoteConversation(
                conversation.id,
                conversation.roomId.orEmpty(),
                conversation.title,
                conversation.updatedAt,
            )
        }
    }

    @Insert
    suspend fun insertMessage(message: CachedMessageEntity)

    @Insert
    suspend fun insertMessages(messages: List<CachedMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Transaction
    suspend fun replaceMessages(conversationId: String, messages: List<CachedMessageEntity>) {
        deleteMessages(conversationId)
        if (messages.isNotEmpty()) insertMessages(messages)
    }

    @Query("UPDATE chat_conversations SET roomId = :roomId, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun setRoom(conversationId: String, roomId: String, updatedAt: Long)

    @Query("UPDATE chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateTitle(conversationId: String, title: String, updatedAt: Long)

    @Query("DELETE FROM chat_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("UPDATE chat_conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touch(conversationId: String, updatedAt: Long)
}

@Database(entities = [CachedConversationEntity::class, CachedMessageEntity::class], version = 1, exportSchema = false)
abstract class ChatCacheDatabase : RoomDatabase() {
    abstract fun dao(): ChatCacheDao

    companion object {
        @Volatile private var instance: ChatCacheDatabase? = null

        fun get(context: Context): ChatCacheDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChatCacheDatabase::class.java,
                "chat360_chat_cache.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
