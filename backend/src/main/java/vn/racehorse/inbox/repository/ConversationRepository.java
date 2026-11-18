package vn.racehorse.inbox.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.inbox.entity.Conversation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Locks the row so a close and a send of one conversation take turns (TECH-243). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") Long id);

    /** The one visibility rule: a participant, or the viewer holds the role the conversation is addressed to (TECH-240). */
    @Query(value = "select c.id from conversation c where c.to_role = :role or exists ("
            + "select 1 from conversation_participant p where p.conversation_id = c.id and p.account_id = :accountId)",
            nativeQuery = true)
    List<Long> visibleIds(@Param("accountId") Long accountId, @Param("role") String role);

    @Query(value = "select exists (select 1 from conversation c where c.id = :id and (c.to_role = :role or exists ("
            + "select 1 from conversation_participant p where p.conversation_id = c.id and p.account_id = :accountId)))",
            nativeQuery = true)
    boolean isVisible(@Param("id") Long id, @Param("accountId") Long accountId, @Param("role") String role);

    @Query(value = "select account_id from conversation_participant where conversation_id = :id", nativeQuery = true)
    List<Long> participantIds(@Param("id") Long conversationId);

    @Query(value = "select conversation_id, account_id from conversation_participant where conversation_id in (:ids)",
            nativeQuery = true)
    List<Object[]> participantsOf(@Param("ids") Collection<Long> conversationIds);

    @Modifying
    @Query(value = "insert into conversation_participant (conversation_id, account_id) values (:id, :accountId)",
            nativeQuery = true)
    void addParticipant(@Param("id") Long conversationId, @Param("accountId") Long accountId);

    /** Moves the read mark forward only. */
    @Modifying
    @Query(value = "insert into conversation_read (conversation_id, account_id, last_read_message_id) "
            + "values (:id, :accountId, :messageId) on conflict (conversation_id, account_id) "
            + "do update set last_read_message_id = greatest(conversation_read.last_read_message_id, excluded.last_read_message_id)",
            nativeQuery = true)
    void markRead(@Param("id") Long conversationId, @Param("accountId") Long accountId, @Param("messageId") Long messageId);

    /** Unread messages per conversation for one person: messages of others after the read mark (BR-233). */
    @Query(value = "select m.conversation_id, count(*) from message m where m.conversation_id in (:ids) "
            + "and m.author_id <> :accountId and m.id > coalesce((select r.last_read_message_id from conversation_read r "
            + "where r.conversation_id = m.conversation_id and r.account_id = :accountId), 0) group by m.conversation_id",
            nativeQuery = true)
    List<Object[]> unreadPerConversation(@Param("ids") Collection<Long> conversationIds, @Param("accountId") Long accountId);
}
