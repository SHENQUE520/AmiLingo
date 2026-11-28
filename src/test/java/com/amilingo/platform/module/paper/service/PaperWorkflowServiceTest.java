package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.dto.PaperWorkflowDTO.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaperWorkflowServiceTest {
    @Mock PaperRepository paperRepo; @Mock PaperOutlineSectionRepository outlineRepo;
    @Mock PaperDraftVersionRepository versionRepo; @Mock PaperSubmissionRepository submissionRepo;
    @Mock PaperFeedbackRepository feedbackRepo; @Mock PaperReviewRecordRepository reviewRepo;
    @Mock PaperStatusHistoryRepository historyRepo; @Mock PaperAccessService access;
    AutoCloseable mocks; PaperWorkflowService service;
    @BeforeEach void setUp(){mocks=MockitoAnnotations.openMocks(this);service=new PaperWorkflowService(paperRepo,outlineRepo,versionRepo,submissionRepo,feedbackRepo,reviewRepo,historyRepo,access);
        when(paperRepo.lockById(1L)).thenAnswer(i -> Optional.ofNullable(access.requireEdit(1L,7L)));}
    @AfterEach void close()throws Exception{mocks.close();}

    @Test void updateTopicPersistsCompleteTopic(){Paper p=paper();when(access.requireEdit(1L,7L)).thenReturn(p);when(paperRepo.save(any())).thenAnswer(i->i.getArgument(0));
        Paper result=service.updateTopic(1L,new TopicRequest(7L,"AI论文","背景","人工智能","目标","问题","AI,教育","导师课题","张老师",LocalDate.of(2027,6,1)));
        assertEquals("AI论文",result.getTitle());assertEquals("人工智能",result.getResearchDirection());assertEquals("张老师",result.getSupervisor());}

    @Test void createVersionIncrementsVersionAndCountsWords(){Paper p=paper();p.setTopicStatus("DRAFT");when(access.requireEdit(1L,7L)).thenReturn(p);
        when(versionRepo.findByPaperIdAndCurrentVersionTrue(1L)).thenReturn(Optional.empty());when(versionRepo.findFirstByPaperIdOrderByVersionNoDesc(1L)).thenReturn(Optional.of(PaperDraftVersion.builder().versionNo(2).build()));
        when(versionRepo.save(any())).thenAnswer(i->i.getArgument(0));when(paperRepo.save(any())).thenAnswer(i->i.getArgument(0));
        PaperDraftVersion v=service.createVersion(1L,new DraftVersionRequest(7L,"REVISION","中文 test words","修改"));
        assertEquals(3,v.getVersionNo());assertEquals(4,v.getWordCount());assertEquals("CONFIRMED",p.getTopicStatus());}

    @Test void submissionCreatesRoundAndStatusHistory(){Paper p=paper();when(access.requireEdit(1L,7L)).thenReturn(p);when(submissionRepo.findFirstByPaperIdOrderByRoundNoDesc(1L)).thenReturn(Optional.empty());
        when(submissionRepo.save(any())).thenAnswer(i->i.getArgument(0));when(paperRepo.save(any())).thenAnswer(i->i.getArgument(0));
        PaperSubmission s=service.createSubmission(1L,new SubmissionRequest(7L,"Journal A","JOURNAL",null,"M-1","Author",null,null,LocalDate.of(2026,1,1),null,List.of("稿件.pdf")));
        assertEquals(1,s.getRoundNo());assertEquals("SUBMITTED",p.getStatus());verify(historyRepo).save(any(PaperStatusHistory.class));}

    @Test void feedbackCannotResolveWithoutNewVersion(){Paper p=paper();PaperFeedback f=PaperFeedback.builder().id(2L).paperId(1L).status("PENDING").build();
        when(feedbackRepo.findById(2L)).thenReturn(Optional.of(f));when(access.requireEdit(1L,7L)).thenReturn(p);
        assertThrows(IllegalArgumentException.class,()->service.updateFeedbackProgress(2L,new FeedbackProgressRequest(7L,"RESOLVED","已修改",null)));}

    @Test void reviewRejectsInvalidRevisionDeadline(){active("UNDER_REVIEW");
        ReviewRecordRequest r=new ReviewRecordRequest(7L,10L,1,"MAJOR_REVISION",LocalDate.of(2026,5,10),"修改",LocalDate.of(2026,5,1),null,List.of());
        assertThrows(IllegalArgumentException.class,()->service.createReviewRecord(1L,r));verify(reviewRepo,never()).save(any());}

    private Paper paper(){return Paper.builder().id(1L).userId(7L).title("论文").status("WRITING").topicStatus("DRAFT").build();}

    private PaperSubmission active(String status) {
        PaperSubmission s=PaperSubmission.builder().id(10L).paperId(1L).roundNo(1).status(status).submissionDate(LocalDate.of(2026,1,1)).build();
        when(submissionRepo.findById(10L)).thenReturn(Optional.of(s));
        when(submissionRepo.findPaperIdById(10L)).thenReturn(Optional.of(1L));
        when(submissionRepo.findFirstByPaperIdOrderByRoundNoDesc(1L)).thenReturn(Optional.of(s));
        when(access.requireEdit(1L,7L)).thenReturn(paper());
        when(reviewRepo.save(any())).thenAnswer(i->i.getArgument(0));
        return s;
    }
    private ReviewRecordRequest review(String status, Long version) {return new ReviewRecordRequest(7L,10L,1,status,LocalDate.of(2026,2,1),"意见",null,version,List.of());}

    @Test void reviewSynchronizesSubmissionAndPaper() {
        PaperSubmission s=active("UNDER_REVIEW");
        service.createReviewRecord(1L,review("MAJOR_REVISION",null));
        assertEquals("REVISION",s.getStatus());verify(submissionRepo).save(s);
        verify(paperRepo).save(argThat(p->"SUBMITTED".equals(p.getStatus())));
    }
    @Test void acceptedProofDoesNotResetPaperToSubmitted() {
        PaperSubmission s=active("ACCEPTED");service.createReviewRecord(1L,review("PROOF",null));
        assertEquals("ACCEPTED",s.getStatus());verify(paperRepo).save(argThat(p->"ACCEPTED".equals(p.getStatus())));
    }
    @Test void revisionRequiresReplyVersionAndRejectsOtherPapersVersion() {
        active("REVISION");assertThrows(IllegalArgumentException.class,()->service.createReviewRecord(1L,review("REVISION_SUBMITTED",null)));
        when(versionRepo.findById(99L)).thenReturn(Optional.of(PaperDraftVersion.builder().paperId(2L).build()));
        assertThrows(IllegalArgumentException.class,()->service.createReviewRecord(1L,review("REVISION_SUBMITTED",99L)));verify(reviewRepo,never()).save(any());
    }
    @Test void revisionWithOwnedVersionReturnsToReview() {
        PaperSubmission s=active("REVISION");when(versionRepo.findById(99L)).thenReturn(Optional.of(PaperDraftVersion.builder().paperId(1L).build()));
        service.createReviewRecord(1L,review("REVISION_SUBMITTED",99L));assertEquals("UNDER_REVIEW",s.getStatus());
    }
    @Test void oldSubmissionCannotOverwriteCurrentStatus() {
        active("UNDER_REVIEW");when(submissionRepo.findFirstByPaperIdOrderByRoundNoDesc(1L)).thenReturn(Optional.of(PaperSubmission.builder().id(11L).build()));
        assertThrows(IllegalStateException.class,()->service.changeSubmissionStatus(10L,new SubmissionStatusRequest(7L,"REJECTED",null)));verify(paperRepo,never()).save(any());
    }
    @Test void terminalSubmissionCannotReopen() {
        active("REJECTED");assertThrows(IllegalStateException.class,()->service.changeSubmissionStatus(10L,new SubmissionStatusRequest(7L,"UNDER_REVIEW",null)));
    }
    @Test void rejectsConcurrentActiveSubmission() {
        PaperSubmission s=active("UNDER_REVIEW");when(submissionRepo.findByPaperIdOrderByRoundNoDesc(1L)).thenReturn(List.of(s));
        assertThrows(IllegalStateException.class,()->service.createSubmission(1L,new SubmissionRequest(7L,"Next","JOURNAL",null,null,null,null,null,LocalDate.of(2026,2,1),null,List.of())));verify(submissionRepo,never()).save(any());
    }
    @Test void savingRevisionPreservesSubmissionState() {
        Paper p=paper();p.setStatus("SUBMITTED");when(access.requireEdit(1L,7L)).thenReturn(p);when(versionRepo.save(any())).thenAnswer(i->i.getArgument(0));
        service.createVersion(1L,new DraftVersionRequest(7L,"REVISION","new content",null));assertEquals("SUBMITTED",p.getStatus());
    }
    @Test void rejectsReviewBeforeSubmissionAndBackwardRounds() {
        active("UNDER_REVIEW");assertThrows(IllegalArgumentException.class,()->service.createReviewRecord(1L,new ReviewRecordRequest(7L,10L,1,"UNDER_REVIEW",LocalDate.of(2025,1,1),null,null,null,List.of())));
        when(reviewRepo.findByPaperIdOrderByStatusDateDescIdDesc(1L)).thenReturn(List.of(PaperReviewRecord.builder().submissionId(10L).reviewRound(2).statusDate(LocalDate.of(2026,1,20)).status("UNDER_REVIEW").build()));
        assertThrows(IllegalArgumentException.class,()->service.createReviewRecord(1L,review("ACCEPTED",null)));verify(reviewRepo,never()).save(any());
    }
    @Test void rejectedSubmissionCanBeResubmittedAsNextRound() {
        PaperSubmission s=active("REJECTED");when(submissionRepo.findByPaperIdOrderByRoundNoDesc(1L)).thenReturn(List.of(s));when(submissionRepo.save(any())).thenAnswer(i->i.getArgument(0));
        PaperSubmission next=service.createSubmission(1L,new SubmissionRequest(7L,"Next","JOURNAL",null,null,null,null,null,LocalDate.of(2026,2,1),null,List.of()));
        assertEquals(2,next.getRoundNo());assertEquals("SUBMITTED",next.getStatus());assertEquals("REJECTED",s.getStatus());
    }
    @Test void publicationRequiresAcceptanceAndStoresPublicationDate() {
        active("UNDER_REVIEW");assertThrows(IllegalStateException.class,()->service.createReviewRecord(1L,review("PUBLISHED",null)));
        active("ACCEPTED");service.createReviewRecord(1L,review("PUBLISHED",null));
        verify(paperRepo).save(argThat(p->"PUBLISHED".equals(p.getStatus())&&LocalDate.of(2026,2,1).equals(p.getPublicationDate())));
    }
    @Test void unauthorizedEditorCannotMutateSubmission() {
        active("UNDER_REVIEW");when(paperRepo.lockById(1L)).thenReturn(Optional.of(paper()));when(access.requireEdit(1L,7L)).thenThrow(new SecurityException("无权限"));
        assertThrows(SecurityException.class,()->service.changeSubmissionStatus(10L,new SubmissionStatusRequest(7L,"ACCEPTED",null)));verify(submissionRepo,never()).save(any());verify(paperRepo,never()).save(any());
    }
}
