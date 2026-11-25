package com.amilingo.platform.module.research.service;

import com.amilingo.platform.module.research.dto.ResearchWorkflowDTO.*;
import com.amilingo.platform.module.research.entity.*;
import com.amilingo.platform.module.research.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResearchWorkflowServiceTest {
    @Mock ResearchProjectRepository projectRepo;
    @Mock ProjectMemberRepository memberRepo;
    @Mock ProjectJoinApplicationRepository joinRepo;
    @Mock ProjectMilestoneRepository milestoneRepo;
    @Mock ProjectTaskRepository taskRepo;
    @Mock TaskProgressLogRepository progressLogRepo;
    @Mock AdvisorRecordRepository advisorRepo;
    @Mock ProjectDeliverableRepository deliverableRepo;
    AutoCloseable mocks;
    ResearchWorkflowService service;

    @BeforeEach void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new ResearchWorkflowService(projectRepo, memberRepo, joinRepo, milestoneRepo,
                taskRepo, progressLogRepo, advisorRepo, deliverableRepo);
    }
    @AfterEach void tearDown() throws Exception { mocks.close(); }

    @Test void ownerCanSubmitDraftProject() {
        ResearchProject project = project(10L, 1L, "DRAFT");
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(projectRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ResearchProject result = service.submitProject(10L, new SubmitProjectRequest(1L));

        assertEquals("PENDING", result.getApplicationStatus());
        assertNotNull(result.getSubmittedAt());
        verify(projectRepo).save(project);
    }

    @Test void nonOwnerCannotSubmitProject() {
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project(10L, 1L, "DRAFT")));
        assertThrows(SecurityException.class,
                () -> service.submitProject(10L, new SubmitProjectRequest(2L)));
        verify(projectRepo, never()).save(any());
    }

    @Test void approvingJoinApplicationCreatesActiveMember() {
        ResearchProject project = project(10L, 1L, "APPROVED");
        ProjectJoinApplication application = ProjectJoinApplication.builder()
                .id(20L).projectId(10L).applicantUserId(2L).status("PENDING").build();
        when(joinRepo.findById(20L)).thenReturn(Optional.of(application));
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(memberRepo.existsByProjectIdAndUserIdAndStatus(10L, 2L, "ACTIVE")).thenReturn(false);
        when(joinRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ProjectJoinApplication result = service.reviewJoin(20L,
                new ReviewJoinRequest(1L, "APPROVED", "欢迎加入", "MEMBER"));

        assertEquals("APPROVED", result.getStatus());
        ArgumentCaptor<ProjectMember> member = ArgumentCaptor.forClass(ProjectMember.class);
        verify(memberRepo).save(member.capture());
        assertEquals(2L, member.getValue().getUserId());
        assertEquals("ACTIVE", member.getValue().getStatus());
    }

    @Test void assigneeCanCompleteTaskAndProgressIsRecorded() {
        ResearchProject project = project(10L, 1L, "APPROVED");
        project.setProgress(0);
        ProjectTask task = ProjectTask.builder().id(30L).projectId(10L).assigneeUserId(2L)
                .status("IN_PROGRESS").progress(40).build();
        when(taskRepo.findById(30L)).thenReturn(Optional.of(task));
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project));
        when(memberRepo.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.of(
                ProjectMember.builder().projectId(10L).userId(2L).role("MEMBER").status("ACTIVE").build()));
        when(taskRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(taskRepo.findByProjectIdOrderByDueDateAscIdAsc(10L)).thenReturn(List.of(task));
        when(milestoneRepo.findByProjectIdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of());
        when(projectRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ProjectTask result = service.updateTaskProgress(30L,
                new TaskProgressRequest(2L, 100, "COMPLETED", "任务完成"));

        assertEquals(100, result.getProgress());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(100, project.getProgress());
        assertEquals("COMPLETED", project.getStatus());
        ArgumentCaptor<TaskProgressLog> log = ArgumentCaptor.forClass(TaskProgressLog.class);
        verify(progressLogRepo).save(log.capture());
        assertEquals(40, log.getValue().getPreviousProgress());
        assertEquals(100, log.getValue().getCurrentProgress());
    }

    @Test void onlyPatentDisclosureCanLinkPatent() {
        ProjectDeliverable deliverable = ProjectDeliverable.builder().id(40L).projectId(10L)
                .creatorUserId(1L).type("REPORT").status("DRAFT").build();
        when(deliverableRepo.findById(40L)).thenReturn(Optional.of(deliverable));
        when(projectRepo.findById(10L)).thenReturn(Optional.of(project(10L, 1L, "APPROVED")));

        assertThrows(IllegalArgumentException.class,
                () -> service.linkPatent(40L, new LinkPatentRequest(1L, 99L)));
        verify(deliverableRepo, never()).save(any());
    }

    private ResearchProject project(Long id, Long ownerId, String approvalStatus) {
        return ResearchProject.builder().id(id).userId(ownerId).projectName("科研项目")
                .status("ONGOING").applicationStatus(approvalStatus).progress(0).build();
    }
}
