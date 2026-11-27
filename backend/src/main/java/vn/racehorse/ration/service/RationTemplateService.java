package vn.racehorse.ration.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.ration.dto.RationItemInput;
import vn.racehorse.ration.dto.RationMealInput;
import vn.racehorse.ration.dto.TemplateRequest;
import vn.racehorse.ration.entity.RationTag;
import vn.racehorse.ration.entity.RationTemplate;
import vn.racehorse.ration.entity.RationTemplateItem;
import vn.racehorse.ration.entity.RationTemplateMeal;
import vn.racehorse.ration.repository.RationTemplateRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.util.List;
import java.util.Map;

/** Ration templates: read by whoever composes rations, changed by the Veterinarian only (BR-249, PERM-065). */
@Service
public class RationTemplateService {

    private final RationTemplateRepository templates;
    private final RationService rations;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public RationTemplateService(RationTemplateRepository templates, RationService rations,
                                 UserAccountRepository accounts, AccessGuard access, AuditService audit,
                                 ClubClock clock) {
        this.templates = templates;
        this.rations = rations;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /** A template's meals in the shape of a ration save, so the same validation applies. */
    static List<RationMealInput> toInputs(RationTemplate t) {
        return t.getMeals().stream().map(m -> new RationMealInput(m.getName(), m.getTime().toString(),
                m.getItems().stream().map(i -> new RationItemInput(i.getType().name(), i.getSupplyItem().getId(),
                        i.getQuantity().toPlainString())).toList())).toList();
    }

    @Transactional(readOnly = true)
    public List<RationTemplate> list() {
        access.require(Permission.COMPOSE_RATION);
        return templates.findAllByOrderByActiveDescNameAsc();
    }

    @Transactional
    public RationTemplate create(TemplateRequest request) {
        AccountPrincipal actor = access.require(Permission.MANAGE_RATION_TEMPLATES);
        UserAccount creator = accounts.findById(actor.id()).orElseThrow();
        RationTemplate t = new RationTemplate("", RationTag.NORMAL, creator, clock.now());
        fill(t, request, null);
        templates.saveAndFlush(t);
        audit.record(actor, "RATION_TEMPLATE_CREATED", "RATION_TEMPLATE", t.getId(),
                Map.of("name", t.getName(), "tag", t.getTag().name()));
        return t;
    }

    @Transactional
    public RationTemplate update(Long id, TemplateRequest request) {
        AccountPrincipal actor = access.require(Permission.MANAGE_RATION_TEMPLATES);
        RationTemplate t = find(id);
        if (!t.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_INACTIVE", "Mẫu khẩu phần đã ngừng dùng.");
        }
        fill(t, request, id);
        t.confirm(); // an edit by the Veterinarian confirms the template (BR-265)
        templates.saveAndFlush(t);
        audit.record(actor, "RATION_TEMPLATE_UPDATED", "RATION_TEMPLATE", id,
                Map.of("name", t.getName(), "tag", t.getTag().name()));
        return t;
    }

    /** The Veterinarian accepts the suggested quantities as they are: the "chờ xác nhận" mark goes (AC-611). */
    @Transactional
    public RationTemplate confirm(Long id) {
        AccountPrincipal actor = access.require(Permission.MANAGE_RATION_TEMPLATES);
        RationTemplate t = find(id);
        if (!t.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_INACTIVE", "Mẫu khẩu phần đã ngừng dùng.");
        }
        if (t.isNeedsConfirmation()) {
            t.confirm();
            audit.record(actor, "RATION_TEMPLATE_CONFIRMED", "RATION_TEMPLATE", id, Map.of("name", t.getName()));
        }
        return t;
    }

    @Transactional
    public RationTemplate deactivate(Long id) {
        AccountPrincipal actor = access.require(Permission.MANAGE_RATION_TEMPLATES);
        RationTemplate t = find(id);
        if (t.isActive()) {
            t.deactivate();
            audit.record(actor, "RATION_TEMPLATE_DEACTIVATED", "RATION_TEMPLATE", id, Map.of("name", t.getName()));
        }
        return t;
    }

    private RationTemplate find(Long id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("mẫu khẩu phần"));
    }

    private void fill(RationTemplate t, TemplateRequest request, Long selfId) {
        if (request == null) {
            throw invalid("name", "Thiếu nội dung mẫu.");
        }
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw invalid("name", "Tên mẫu bắt buộc, tối đa 100 ký tự.");
        }
        RationTag tag = request.tag() == null || request.tag().isBlank() ? null : RationTag.parse(request.tag());
        if (tag == null) {
            throw invalid("tag", "Chọn loại khẩu phần.");
        }
        if (templates.existsByNameIgnoreCaseAndActiveTrueAndIdNot(name, selfId == null ? -1L : selfId)) {
            throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NAME_TAKEN", "Tên mẫu \"" + name + "\" đã tồn tại.",
                    Map.of("name", "Tên mẫu đã tồn tại."));
        }
        List<RationService.Normal> normal = rations.validate(request.meals());
        t.rename(name, tag);
        t.clearMeals();
        int mealPos = 0;
        for (RationService.Normal m : normal) {
            RationTemplateMeal meal = new RationTemplateMeal(t, mealPos++, m.name(), m.time());
            int itemPos = 0;
            for (RationService.NormalItem i : m.items()) {
                meal.addItem(new RationTemplateItem(meal, itemPos++, i.type(), i.supply(), i.quantity()));
            }
            t.addMeal(meal);
        }
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, Map.of(field, message));
    }
}
