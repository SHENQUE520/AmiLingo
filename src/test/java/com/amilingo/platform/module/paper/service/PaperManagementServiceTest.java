package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.dto.PaperManagementDTO.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.*;
import java.time.LocalDate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaperManagementServiceTest {
    @Mock PaperRepository paperRepo; @Mock PaperAuthorRepository authorRepo; @Mock PublicationVenueRepository venueRepo;
    @Mock PaperAttachmentRepository attachmentRepo; @Mock PaperCollaboratorRepository collaboratorRepo; @Mock PaperAccessService access;
    AutoCloseable mocks; PaperManagementService service;
    @BeforeEach void setUp(){mocks=MockitoAnnotations.openMocks(this);service=new PaperManagementService(paperRepo,authorRepo,venueRepo,attachmentRepo,collaboratorRepo,access);}
    @AfterEach void close()throws Exception{mocks.close();}

    @Test void duplicateAuthorOrderIsRejected(){when(authorRepo.existsByPaperIdAndAuthorOrder(1L,1)).thenReturn(true);
        AuthorRequest r=new AuthorRequest(7L,null,"作者","学校",1,false,false,"实验");
        assertThrows(IllegalArgumentException.class,()->service.addAuthor(1L,r));verify(authorRepo,never()).save(any());}

    @Test void attachmentMetadataIsStored(){when(attachmentRepo.save(any())).thenAnswer(i->i.getArgument(0));
        PaperAttachment a=service.addAttachment(1L,new AttachmentRequest(7L,null,"MANUSCRIPT","paper.pdf","application/pdf",100L,"papers/key",null,"sha256",1));
        assertEquals("papers/key",a.getStorageKey());assertEquals("ACTIVE",a.getStatus());verify(access).requireEdit(1L,7L);}

    @Test void onlyOwnerCanAddCollaborator(){when(collaboratorRepo.findByPaperIdAndUserId(1L,8L)).thenReturn(Optional.empty());when(collaboratorRepo.save(any())).thenAnswer(i->i.getArgument(0));
        PaperCollaborator c=service.addCollaborator(1L,new CollaboratorRequest(7L,8L,"VIEWER"));
        assertEquals(8L,c.getUserId());assertEquals("VIEWER",c.getRole());verify(access).requireOwner(1L,7L);}

    @Test void anotherUserCannotUpdateVenue(){PublicationVenue v=PublicationVenue.builder().id(3L).ownerUserId(7L).build();when(venueRepo.findById(3L)).thenReturn(Optional.of(v));
        VenueRequest r=new VenueRequest(8L,"Venue","JOURNAL",null,null,null,null,null,BigDecimal.ONE,null,false,false,null,30,BigDecimal.ZERO,"CNY",null);
        assertThrows(SecurityException.class,()->service.updateVenue(3L,r));}

    @Test void deletedAttachmentUsesSoftDelete(){PaperAttachment a=PaperAttachment.builder().id(4L).paperId(1L).status("ACTIVE").build();when(attachmentRepo.findById(4L)).thenReturn(Optional.of(a));
        service.deleteAttachment(4L,7L);assertEquals("DELETED",a.getStatus());verify(attachmentRepo).save(a);}

    @Test void statisticsCalculateAcceptanceRateAndReviewDays(){Paper accepted=Paper.builder().id(1L).userId(7L).status("ACCEPTED").level("SCI Q1")
            .submissionDate(LocalDate.of(2026,1,1)).acceptanceDate(LocalDate.of(2026,1,31)).publicationDate(LocalDate.of(2026,2,1)).build();
        Paper pending=Paper.builder().id(2L).userId(7L).status("SUBMITTED").level("SCI Q2").submissionDate(LocalDate.of(2026,2,1)).build();
        when(paperRepo.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(accepted,pending));
        when(collaboratorRepo.findByUserIdAndStatus(7L,"ACTIVE")).thenReturn(List.of());
        when(paperRepo.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(accepted,pending)));
        PaperStatistics stats=service.statistics(7L);assertEquals(2,stats.total());assertEquals(50.0,stats.acceptanceRate());assertEquals(30.0,stats.averageReviewDays());}
}
