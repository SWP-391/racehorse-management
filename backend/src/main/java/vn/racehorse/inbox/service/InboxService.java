package vn.racehorse.inbox.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.inbox.dto.ConversationDetail;
import vn.racehorse.inbox.dto.ConversationRow;
import vn.racehorse.inbox.dto.MessageDto;
import vn.racehorse.inbox.dto.RecipientOptions;
import vn.racehorse.inbox.dto.StartRequest;
import vn.racehorse.inbox.dto.ThreadDetail;
import vn.racehorse.inbox.dto.ThreadMessage;
import vn.racehorse.inbox.dto.ThreadRow;
import vn.racehorse.inbox.dto.ThreadTopic;
import vn.racehorse.inbox.entity.Conversation;
import vn.racehorse.inbox.entity.Message;
import vn.racehorse.inbox.repository.ConversationRepository;
import vn.racehorse.inbox.repository.MessageRepository;
import vn.racehorse.notification.repository.NotificationRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The inbox: conversations between the people BR-228 allows, kept for ever and never edited (BR-227). A conversation
 * is visible to its participants, and to every Club Manager when it is addressed to the role (BR-231, TECH-240).
 */
@Service
public class InboxService {

    public static final int SUBJECT_MAX = 120;
    public static final int TEXT_MAX = 2000;
    private static final String CLUB_MANAGERS = "Quản lý CLB";
    private static final int PREVIEW = 100;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserAccountRepository accounts;
    private final HorseRepository horses;
    private final MessagingPolicy policy;
    private final NotificationService notifications;
    private final NotificationRepository notices;
    private final AccessGuard access;
    private final ClubClock clock;

    public InboxService(ConversationRepository conversations, MessageRepository messages, UserAccountRepository accounts,
                        HorseRepository horses, MessagingPolicy policy, NotificationService notifications,
                        NotificationRepository notices, AccessGuard access, ClubClock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.accounts = accounts;
        this.horses = horses;
        this.policy = policy;
        this.notifications = notifications;
        this.notices = notices;
        this.access = access;
        this.clock = clock;
    }

    private AccountPrincipal user() {
        return access.require(Permission.MESSAGING);
    }

    private UserAccount me(AccountPrincipal user) {
        return accounts.findById(user.id()).orElseThrow(ApiException::forbidden);
    }

    private static String role(AccountPrincipal user) {
        return user.role().name();
    }

    // ---- the form -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RecipientOptions options(Long horseId) {
        UserAccount sender = me(user());
        Horse horse = horseId == null ? null : horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse != null && sender.getRole() != Role.CLUB_MANAGER) {
            policy.check(sender, null, horse);
        }
        return new RecipientOptions(policy.horseOptions(sender), policy.offered(sender, horse));
    }

    // ---- write ----------------------------------------------------------------------------------------------

    @Transactional
    public Long start(StartRequest request) {
        AccountPrincipal caller = user();
        UserAccount sender = me(caller);
        String subject = trimmed(request.subject(), "subject", "Tiêu đề", SUBJECT_MAX);
        String text = trimmed(request.text(), "text", "Nội dung", TEXT_MAX);
        boolean toRole = Boolean.TRUE.equals(request.toClubManagers());
        if (toRole == (request.recipientId() != null)) {
            throw ApiException.invalid("RECIPIENT_REQUIRED", "recipient", "Bắt buộc chọn người nhận.");
        }
        boolean feedback = Conversation.FEEDBACK.equalsIgnoreCase(request.kind() == null ? "" : request.kind().strip());
        if (request.kind() != null && !feedback && !Conversation.MESSAGE.equalsIgnoreCase(request.kind().strip())) {
            throw ApiException.invalid("VALIDATION_FAILED", "kind", "Loại tin nhắn không hợp lệ.");
        }
        if (feedback && (!toRole || sender.getRole() == Role.CLUB_MANAGER)) {
            throw ApiException.invalid("VALIDATION_FAILED", "kind", "Phản hồi chỉ gửi tới Quản lý CLB.");
        }
        UserAccount recipient = toRole ? null : accounts.findById(request.recipientId())
                .orElseThrow(() -> ApiException.notFound("người nhận"));
        Horse horse = request.horseId() == null ? null
                : horses.findById(request.horseId()).orElseThrow(() -> ApiException.notFound("ngựa"));
        policy.check(sender, recipient, horse);

        Instant now = clock.now();
        Conversation c = conversations.save(new Conversation(subject, horse == null ? null : horse.getId(),
                toRole ? Conversation.CLUB_MANAGER : null, sender.getId(), now,
                feedback ? Conversation.FEEDBACK : Conversation.MESSAGE));
        conversations.addParticipant(c.getId(), sender.getId());
        if (recipient != null) {
            conversations.addParticipant(c.getId(), recipient.getId());
        }
        Message first = messages.save(new Message(c.getId(), sender.getId(), text, now));
        conversations.markRead(c.getId(), sender.getId(), first.getId());
        notifyOthers(c, sender.getId());
        return c.getId();
    }

    @Transactional
    public Long send(Long id, String rawText) {
        AccountPrincipal caller = user();
        String text = trimmed(rawText, "text", "Nội dung", TEXT_MAX);
        Conversation c = conversations.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("hội thoại"));
        requireVisible(c, caller);
        if (!c.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED",
                    "Hội thoại đã đóng; hãy mở lại để nhắn tiếp.");
        }
        Message m = messages.save(new Message(c.getId(), caller.id(), text, clock.now()));
        conversations.markRead(c.getId(), caller.id(), m.getId());
        notifyOthers(c, caller.id());
        return m.getId();
    }

    @Transactional
    public void changeState(Long id, boolean open) {
        AccountPrincipal caller = user();
        Conversation c = conversations.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("hội thoại"));
        requireVisible(c, caller);
        if (c.isOpen() != open) {
            c.changeState(open, caller.id(), clock.now());
        }
    }

    private void notifyOthers(Conversation c, Long senderId) {
        Set<Long> to = new LinkedHashSet<>(conversations.participantIds(c.getId()));
        if (c.getToRole() != null) {
            accounts.findByRoleOrderByDisplayNameAsc(Role.valueOf(c.getToRole())).forEach(a -> to.add(a.getId()));
        }
        to.remove(senderId);
        Map<Long, UserAccount> people = accounts.findAllById(to).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        String message = "Tin nhắn mới: " + c.getSubject();
        String link = "/inbox/" + c.getId();
        for (Long id : to) {
            UserAccount a = people.get(id);
            if (a != null && a.isActive()) {
                notifications.notifyAccount(id, message, link);
            }
        }
    }

    // ---- read -----------------------------------------------------------------------------------------------

    /** One conversation as the viewer sees it, with the chat it belongs to. */
    private record Item(ConversationRow row, Side side) {
    }

    /** The other side of a chat: a person, or the Club Manager role as one (BR-231). */
    private record Side(String key, String name, String role) {
    }

    private static final Side CLUB = new Side("club", CLUB_MANAGERS, Role.CLUB_MANAGER.name());

    @Transactional(readOnly = true)
    public List<ConversationRow> list() {
        return items().stream().map(Item::row).toList();
    }

    private List<Item> items() {
        AccountPrincipal caller = user();
        List<Long> ids = conversations.visibleIds(caller.id(), role(caller));
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Message> latest = messages.latestOf(ids).stream()
                .collect(Collectors.toMap(Message::getConversationId, Function.identity()));
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : conversations.unreadPerConversation(ids, caller.id())) {
            unread.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Map<Long, List<Long>> participants = participants(ids);
        List<Conversation> found = conversations.findAllById(ids);
        Map<Long, UserAccount> people = people(found, participants);
        Map<Long, Horse> horseById = horses.findAllById(found.stream().map(Conversation::getHorseId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Horse::getId, Function.identity()));
        return found.stream().map(c -> {
            Message last = latest.get(c.getId());
            Instant at = last == null ? c.getCreatedAt() : last.getSentAt();
            Horse h = c.getHorseId() == null ? null : horseById.get(c.getHorseId());
            Side side = side(c, caller, participants.getOrDefault(c.getId(), List.of()), people);
            return new Item(new ConversationRow(c.getId(), c.getSubject(), h == null ? null : h.getName(), c.getState(),
                    side.name(), last == null ? "" : preview(last.getBody()), at, unread.getOrDefault(c.getId(), 0L),
                    c.getKind(), side.key()), side);
        }).sorted(Comparator.comparing((Item i) -> i.row().lastAt(), Comparator.reverseOrder())
                .thenComparing(i -> i.row().id(), Comparator.reverseOrder())).toList();
    }

    /** The chats of the signed-in person (one per person, the Club Manager role as one), newest activity first. */
    @Transactional(readOnly = true)
    public List<ThreadRow> threads(String q) {
        String needle = q == null ? "" : q.strip().toLowerCase();
        Map<String, List<Item>> byKey = new LinkedHashMap<>();
        for (Item i : items()) {
            byKey.computeIfAbsent(i.side().key(), k -> new ArrayList<>()).add(i);
        }
        List<ThreadRow> out = new ArrayList<>();
        for (List<Item> group : byKey.values()) {
            Item first = group.get(0);
            if (!needle.isEmpty() && !first.side().name().toLowerCase().contains(needle)
                    && group.stream().noneMatch(i -> i.row().subject().toLowerCase().contains(needle))) {
                continue;
            }
            out.add(new ThreadRow(first.side().key(), first.side().name(), first.side().role(), first.row().preview(),
                    first.row().lastAt(), group.stream().mapToLong(i -> i.row().unread()).sum(), group.size(),
                    (int) group.stream().filter(i -> Conversation.OPEN.equals(i.row().state())).count(),
                    group.stream().anyMatch(i -> Conversation.FEEDBACK.equals(i.row().kind()))));
        }
        return out;
    }

    /** Opens a whole chat: every conversation with that person, read and merged in time order (BR-233). */
    @Transactional
    public ThreadDetail thread(String key) {
        AccountPrincipal caller = user();
        List<Item> group = items().stream().filter(i -> i.side().key().equals(key)).toList();
        if (group.isEmpty()) {
            throw ApiException.notFound("hội thoại");
        }
        List<ThreadTopic> topics = new ArrayList<>();
        List<ThreadMessage> all = new ArrayList<>();
        for (Item item : group.stream().sorted(Comparator.comparing(i -> i.row().id())).toList()) {
            ConversationDetail d = open(item.row().id());
            topics.add(new ThreadTopic(d.id(), d.subject(), d.horseName(), d.kind(), d.state(), d.stateBy(), d.stateAt()));
            for (int n = 0; n < d.messages().size(); n++) {
                MessageDto m = d.messages().get(n);
                all.add(new ThreadMessage(m.id(), d.id(), m.authorId(), m.authorName(), m.text(), m.sentAt(), m.mine(),
                        n == 0 && Conversation.FEEDBACK.equals(d.kind())));
            }
        }
        all.sort(Comparator.comparing(ThreadMessage::sentAt).thenComparing(ThreadMessage::id));
        Long open = group.stream().filter(i -> Conversation.OPEN.equals(i.row().state())).map(i -> i.row().id())
                .findFirst().orElse(null);
        Item head = group.get(0);
        return new ThreadDetail(key, head.side().name(), head.side().role(), topics, all, open);
    }

    /** Writes into the chat: the most recently active open conversation; when all are closed the service says so. */
    @Transactional
    public Long sendToThread(String key, String text) {
        List<Item> group = items().stream().filter(i -> i.side().key().equals(key)).toList();
        if (group.isEmpty()) {
            throw ApiException.notFound("hội thoại");
        }
        Item target = group.stream().filter(i -> Conversation.OPEN.equals(i.row().state())).findFirst().orElse(group.get(0));
        return send(target.row().id(), text);
    }

    @Transactional(readOnly = true)
    public long unread() {
        AccountPrincipal caller = user();
        List<Long> ids = conversations.visibleIds(caller.id(), role(caller));
        return ids.isEmpty() ? 0 : conversations.unreadPerConversation(ids, caller.id()).stream()
                .mapToLong(r -> ((Number) r[1]).longValue()).sum();
    }

    /** Opening a conversation marks it read and clears its notifications (BR-233, TECH-243). */
    @Transactional
    public ConversationDetail open(Long id) {
        AccountPrincipal caller = user();
        Conversation c = conversations.findById(id).orElseThrow(() -> ApiException.notFound("hội thoại"));
        requireVisible(c, caller);
        List<Message> all = messages.findByConversationIdOrderByIdAsc(c.getId());
        if (!all.isEmpty()) {
            conversations.markRead(c.getId(), caller.id(), all.get(all.size() - 1).getId());
        }
        Instant now = clock.now();
        notices.findByRecipientIdAndLinkAndReadAtIsNull(caller.id(), "/inbox/" + c.getId())
                .forEach(n -> n.markRead(now));
        Map<Long, List<Long>> participants = participants(List.of(c.getId()));
        Map<Long, UserAccount> people = people(List.of(c), participants);
        all.forEach(m -> people.computeIfAbsent(m.getAuthorId(), a -> accounts.findById(a).orElse(null)));
        Horse horse = c.getHorseId() == null ? null : horses.findById(c.getHorseId()).orElse(null);
        UserAccount stateBy = c.getStateById() == null ? null : accounts.findById(c.getStateById()).orElse(null);
        List<MessageDto> dtos = all.stream().map(m -> {
            UserAccount a = people.get(m.getAuthorId());
            return new MessageDto(m.getId(), m.getAuthorId(), a == null ? "" : a.getDisplayName(), m.getBody(),
                    m.getSentAt(), Objects.equals(m.getAuthorId(), caller.id()));
        }).toList();
        Side side = side(c, caller, participants.getOrDefault(c.getId(), List.of()), people);
        return new ConversationDetail(c.getId(), c.getSubject(), c.getHorseId(), horse == null ? null : horse.getName(),
                c.getState(), side.name(), stateBy == null ? null : stateBy.getDisplayName(), c.getStateAt(), dtos,
                c.getKind(), side.key());
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    /** Anyone who may not see it is told it does not exist (BR-231). */
    private void requireVisible(Conversation c, AccountPrincipal caller) {
        if (!conversations.isVisible(c.getId(), caller.id(), role(caller))) {
            throw ApiException.notFound("hội thoại");
        }
    }

    private Map<Long, List<Long>> participants(List<Long> ids) {
        Map<Long, List<Long>> out = new HashMap<>();
        for (Object[] row : conversations.participantsOf(ids)) {
            out.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>()).add(((Number) row[1]).longValue());
        }
        return out;
    }

    private Map<Long, UserAccount> people(List<Conversation> found, Map<Long, List<Long>> participants) {
        Set<Long> ids = new HashSet<>();
        participants.values().forEach(ids::addAll);
        found.forEach(c -> ids.add(c.getStartedById()));
        return accounts.findAllById(ids).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    /** The other side as the viewer sees it: the role when written to the Club Manager role, else the person. */
    private Side side(Conversation c, AccountPrincipal viewer, List<Long> participantIds, Map<Long, UserAccount> people) {
        boolean manager = viewer.role() == Role.CLUB_MANAGER;
        if (c.getToRole() != null && !manager) {
            return CLUB;
        }
        Long other = c.getToRole() != null ? c.getStartedById()
                : participantIds.stream().filter(p -> !Objects.equals(p, viewer.id())).findFirst().orElse(null);
        UserAccount a = other == null ? null : people.get(other);
        if (a == null) {
            return new Side("none", "", "");
        }
        if (!manager && a.getRole() == Role.CLUB_MANAGER) {
            return CLUB;
        }
        return new Side("u" + a.getId(), a.getDisplayName(), a.getRole().name());
    }

    private static String preview(String body) {
        String flat = body.replaceAll("\\s+", " ");
        return flat.length() <= PREVIEW ? flat : flat.substring(0, PREVIEW) + "…";
    }

    /** Trims, then refuses an empty or too long text naming the field (BR-227). */
    private static String trimmed(String value, String field, String label, int max) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw ApiException.invalid("VALIDATION_FAILED", field, "Bắt buộc nhập.");
        }
        if (text.length() > max) {
            throw ApiException.invalid("VALIDATION_FAILED", field, label + " tối đa " + max + " ký tự.");
        }
        return text;
    }
}
