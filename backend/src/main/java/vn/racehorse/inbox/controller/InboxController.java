package vn.racehorse.inbox.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.inbox.dto.ConversationDetail;
import vn.racehorse.inbox.dto.ConversationRow;
import vn.racehorse.inbox.dto.RecipientOptions;
import vn.racehorse.inbox.dto.SendRequest;
import vn.racehorse.inbox.dto.StartRequest;
import vn.racehorse.inbox.dto.ThreadDetail;
import vn.racehorse.inbox.dto.ThreadRow;
import vn.racehorse.inbox.dto.UnreadCount;
import vn.racehorse.inbox.service.InboxService;

import java.util.Map;

/** The inbox of conversations between allowed roles (PERM-061, TECH-242). */
@RestController
@RequestMapping("/api/inbox")
@Tag(name = "Hộp thư", description = "Hội thoại giữa các vai trò được phép")
public class InboxController {

    private final InboxService inbox;

    public InboxController(InboxService inbox) {
        this.inbox = inbox;
    }

    @Operation(summary = "Ngựa được chọn và người nhận được phép cho ngựa đó")
    @GetMapping("/recipients")
    public RecipientOptions recipients(@RequestParam(required = false) Long horseId) {
        return inbox.options(horseId);
    }

    @Operation(summary = "Số tin nhắn chưa đọc của tôi")
    @GetMapping("/unread")
    public UnreadCount unread() {
        return new UnreadCount(inbox.unread());
    }

    @Operation(summary = "Hội thoại của tôi, mới nhất trước")
    @GetMapping("/conversations")
    public PageResponse<ConversationRow> list(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return Paging.page(inbox.list(), PageParams.of(page, size, null));
    }

    @Operation(summary = "Các cuộc trò chuyện của tôi, mỗi người một cuộc, mới nhất trước")
    @GetMapping("/threads")
    public PageResponse<ThreadRow> threads(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String q) {
        return Paging.page(inbox.threads(q), PageParams.of(page, size, null));
    }

    @Operation(summary = "Mở một cuộc trò chuyện với một người (đánh dấu đã đọc)")
    @GetMapping("/threads/{key}")
    public ThreadDetail thread(@PathVariable String key) {
        return inbox.thread(key);
    }

    @Operation(summary = "Nhắn vào một cuộc trò chuyện")
    @PostMapping("/threads/{key}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> sendToThread(@PathVariable String key, @Valid @RequestBody SendRequest request) {
        return Map.of("id", inbox.sendToThread(key, request.text()));
    }

    @Operation(summary = "Bắt đầu một hội thoại")
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> start(@Valid @RequestBody StartRequest request) {
        return Map.of("id", inbox.start(request));
    }

    @Operation(summary = "Mở một hội thoại (đánh dấu đã đọc)")
    @GetMapping("/conversations/{id}")
    public ConversationDetail open(@PathVariable Long id) {
        return inbox.open(id);
    }

    @Operation(summary = "Nhắn tiếp trong một hội thoại")
    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> send(@PathVariable Long id, @Valid @RequestBody SendRequest request) {
        return Map.of("id", inbox.send(id, request.text()));
    }

    @Operation(summary = "Đóng một hội thoại")
    @PostMapping("/conversations/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable Long id) {
        inbox.changeState(id, false);
    }

    @Operation(summary = "Mở lại một hội thoại đã đóng")
    @PostMapping("/conversations/{id}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable Long id) {
        inbox.changeState(id, true);
    }
}
