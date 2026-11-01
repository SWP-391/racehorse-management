package vn.racehorse.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.notification.dto.Inbox;
import vn.racehorse.notification.dto.NotificationDto;
import vn.racehorse.notification.service.NotificationService;

@Tag(name = "Thông báo", description = "Thông báo của người dùng đang đăng nhập")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Thông báo của tôi")
    @GetMapping
    public Inbox mine(@RequestParam(required = false) Boolean unread,
                      @RequestParam(required = false) Integer page,
                      @RequestParam(required = false) Integer size) {
        PageParams params = PageParams.of(page, size, null);
        PageResponse<NotificationDto> paged = Paging.page(service.mine().stream()
                .filter(n -> !Boolean.TRUE.equals(unread) || n.getReadAt() == null)
                .map(NotificationDto::of).toList(), params);
        return new Inbox(service.unreadCount(), paged.items(), paged.page(), paged.size(), paged.total());
    }

    @Operation(summary = "Đánh dấu đã đọc")
    @PostMapping("/{id}/read")
    public NotificationDto read(@PathVariable Long id) {
        return NotificationDto.of(service.markRead(id));
    }
}
