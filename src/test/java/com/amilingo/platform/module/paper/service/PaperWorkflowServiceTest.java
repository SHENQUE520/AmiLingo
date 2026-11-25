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
    @BeforeEach void setUp(){mocks=MockitoAnnotations.openMocks(this);service=new PaperWorkflowService(paperRepo,outlineRepo,versionRepo,submissionRepo,feedbackRepo,reviewRepo,historyRepo,access);}
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

    @Test void reviewRejectsInvalidRevisionDeadline(){when(access.requireEdit(1L,7L)).thenReturn(paper());
        ReviewRecordRequest r=new ReviewRecordRequest(7L,null,1,"MAJOR_REVISION",LocalDate.of(2026,5,10),"修改",LocalDate.of(2026,5,1),null,List.of());
        assertThrows(IllegalArgumentException.class,()->service.createReviewRecord(1L,r));verify(reviewRepo,never()).save(any());}

    private Paper paper(){return Paper.builder().id(1L).userId(7L).title("论文").status("WRITING").topicStatus("DRAFT").build();}
}
