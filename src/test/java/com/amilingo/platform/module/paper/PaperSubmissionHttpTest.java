package com.amilingo.platform.module.paper;

import com.amilingo.platform.common.config.security.AuthenticatedUser;
import com.amilingo.platform.module.paper.controller.*;
import com.amilingo.platform.module.paper.service.*;
import com.amilingo.platform.module.paper.dto.PaperWorkflowDTO.*;
import com.amilingo.platform.module.patent.repository.*;
import com.amilingo.platform.module.research.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaperSubmissionHttpTest {
    PaperWorkflowService service; MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(PaperWorkflowService.class);
        var security=new AcademicSecurity(mock(PaperAccessService.class),mock(PatentRepository.class),mock(ResearchProjectRepository.class),mock(ProjectMemberRepository.class),mock(ProjectDeliverableRepository.class),mock(PatentInventorRepository.class));
        var proxy=new AspectJProxyFactory(new PaperWorkflowController(service));proxy.setProxyTargetClass(true);proxy.addAspect(security);
        Object controller=proxy.getProxy();
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new AcademicHttpErrors(),new PaperExceptionHandler()).build();
    }
    void login(){var user=AuthenticatedUser.builder().userId(7L).username("test").role("USER").build();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void anonymousCannotReadSubmissions() throws Exception {
        mvc.perform(get("/api/paper/1/submissions").param("actorUserId","7")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void forgedReviewerCannotWrite() throws Exception {
        login();mvc.perform(post("/api/paper/1/reviews").contentType("application/json").content("""
            {"actorUserId":8,"submissionId":10,"reviewRound":1,"status":"ACCEPTED","statusDate":"2026-02-01"}
            """)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void reviewRequiresSubmission() throws Exception {
        login();mvc.perform(post("/api/paper/1/reviews").contentType("application/json").content("""
            {"actorUserId":7,"reviewRound":1,"status":"ACCEPTED","statusDate":"2026-02-01"}
            """)).andExpect(jsonPath("$.code").value(400));verifyNoInteractions(service);
    }
    @Test void invalidTransitionIsReturnedAsBusinessError() throws Exception {
        login();when(service.changeSubmissionStatus(eq(10L),any())).thenThrow(new IllegalStateException("不允许恢复已结束投稿"));
        mvc.perform(post("/api/paper/submissions/10/status").contentType("application/json").content("""
            {"actorUserId":7,"status":"UNDER_REVIEW"}
            """)).andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.message").value("不允许恢复已结束投稿"));
    }
    @Test void validSubmissionReachesWorkflow() throws Exception {
        login();mvc.perform(post("/api/paper/1/submissions").contentType("application/json").content("""
            {"actorUserId":7,"venueName":"Journal A","venueType":"JOURNAL","submissionDate":"2026-02-01"}
            """)).andExpect(jsonPath("$.code").value(200));verify(service).createSubmission(eq(1L),any(SubmissionRequest.class));
    }
}
