package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.common.config.security.SecurityUtil;
import com.amilingo.platform.module.patent.repository.PatentRepository;
import com.amilingo.platform.module.research.repository.*;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

/** Authentication boundary shared only by the three academic modules. */
@Aspect @Component @RequiredArgsConstructor
public class AcademicSecurity {
    private final PaperAccessService papers;
    private final PatentRepository patents;
    private final ResearchProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectDeliverableRepository deliverables;
    private final com.amilingo.platform.module.patent.repository.PatentInventorRepository inventors;
    private static final Set<String> ACTORS = Set.of("actorUserId", "reviewerUserId", "applicantUserId", "recorderUserId", "creatorUserId");

    public long currentUser() {
        var user = SecurityUtil.getCurrentAuthenticatedUser();
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || user == null || user.getUserId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user.getUserId();
    }
    public void access(String domain, Long id, boolean edit) {
        long user = currentUser();
        if ("paper".equals(domain)) {
            if (edit) papers.requireEdit(id, user); else papers.requireRead(id, user);
        } else if ("patent".equals(domain)) {
            var p = patents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            same(p.getUserId(), user);
        } else if ("research".equals(domain)) {
            var p = projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (p.getUserId().equals(user)) return;
            var m = members.findByProjectIdAndUserId(id, user).filter(x -> "ACTIVE".equals(x.getStatus()));
            if (m.isEmpty() || (edit && !"MANAGER".equals(m.get().getRole()))) denied();
        } else throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private void same(Object claimed, long user) { if (!Long.valueOf(user).equals(claimed)) denied(); }
    private void denied() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权执行此操作"); }

    @Around("execution(public * com.amilingo.platform.module.paper.controller.*Controller.*(..)) || execution(public * com.amilingo.platform.module.patent.controller.*Controller.*(..)) || execution(public * com.amilingo.platform.module.research.controller.*Controller.*(..))")
    public Object authenticate(ProceedingJoinPoint call) throws Throwable {
        long user = currentUser();
        var method = ((MethodSignature)call.getSignature()).getMethod();
        var args = call.getArgs();
        var params = method.getParameters();
        for (int i=0; i<args.length; i++) {
            Object arg = args[i];
            if (ACTORS.contains(params[i].getName()) || "userId".equals(params[i].getName())) same(arg, user);
            if (arg == null) continue;
            if (arg.getClass().isRecord()) {
                for (var field : arg.getClass().getRecordComponents())
                    if (ACTORS.contains(field.getName())) same(field.getAccessor().invoke(arg), user);
                String type = arg.getClass().getSimpleName();
                if (type.equals("DisclosureActionRequest")) {
                    String action = (String)arg.getClass().getMethod("action").invoke(arg);
                    if (Set.of("APPROVE", "RETURN").contains(action) && !SecurityUtil.isAdmin()) denied();
                }
                if (type.equals("LinkPatentRequest")) access("patent", (Long)arg.getClass().getMethod("patentId").invoke(arg), true);
                if (type.equals("ProjectLinkRequest")) {
                    Long project = (Long)arg.getClass().getMethod("researchProjectId").invoke(arg);
                    access("research", project, false);
                    Long deliverable = (Long)arg.getClass().getMethod("deliverableId").invoke(arg);
                    if (deliverable != null && !deliverables.findById(deliverable).map(x->project.equals(x.getProjectId())).orElse(false)) denied();
                }
                if (type.equals("InventorConfirmationRequest")) {
                    var inventor = inventors.findById((Long)args[0]).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
                    same(inventor.getUserId(), user);
                }
            } else if (Set.of("PaperDTO", "PatentDTO", "ResearchProjectDTO").contains(arg.getClass().getSimpleName())) {
                same(arg.getClass().getMethod("getUserId").invoke(arg), user);
            }
        }
        String controller = method.getDeclaringClass().getSimpleName();
        if (Set.of("PatentController", "ResearchProjectController").contains(controller)
                && Set.of("getById", "update", "delete").contains(method.getName()))
            access(controller.equals("PatentController") ? "patent" : "research", (Long)args[0], !method.getName().equals("getById"));
        if (controller.equals("ResearchWorkflowController") && method.getName().equals("review") && !SecurityUtil.isAdmin()) denied();
        return call.proceed();
    }
}
