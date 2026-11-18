package vn.racehorse.inbox.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.racehorse.inbox.entity.Message;

import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /** The newest message of each given conversation, for the list previews. */
    @Query("select m from Message m where m.id in (select max(m2.id) from Message m2 "
            + "where m2.conversationId in :ids group by m2.conversationId)")
    List<Message> latestOf(@Param("ids") Collection<Long> conversationIds);
}
