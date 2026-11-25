package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperAccessServiceTest {
    @Mock PaperRepository paperRepo; @Mock PaperCollaboratorRepository collaboratorRepo;
    AutoCloseable mocks; PaperAccessService service;
    @BeforeEach void setUp(){mocks=MockitoAnnotations.openMocks(this);service=new PaperAccessService(paperRepo,collaboratorRepo);}
    @AfterEach void close()throws Exception{mocks.close();}
    @Test void ownerHasReadEditAndOwnerAccess(){when(paperRepo.findById(1L)).thenReturn(Optional.of(paper()));assertNotNull(service.requireRead(1L,7L));assertNotNull(service.requireEdit(1L,7L));assertNotNull(service.requireOwner(1L,7L));}
    @Test void editorCanEdit(){when(paperRepo.findById(1L)).thenReturn(Optional.of(paper()));when(collaboratorRepo.findByPaperIdAndUserId(1L,8L)).thenReturn(Optional.of(collaborator("EDITOR","ACTIVE")));assertNotNull(service.requireEdit(1L,8L));}
    @Test void viewerCanReadButCannotEdit(){when(paperRepo.findById(1L)).thenReturn(Optional.of(paper()));when(collaboratorRepo.findByPaperIdAndUserId(1L,8L)).thenReturn(Optional.of(collaborator("VIEWER","ACTIVE")));assertNotNull(service.requireRead(1L,8L));assertThrows(SecurityException.class,()->service.requireEdit(1L,8L));}
    @Test void inactiveCollaboratorCannotRead(){when(paperRepo.findById(1L)).thenReturn(Optional.of(paper()));when(collaboratorRepo.findByPaperIdAndUserId(1L,8L)).thenReturn(Optional.of(collaborator("EDITOR","INACTIVE")));assertThrows(SecurityException.class,()->service.requireRead(1L,8L));}
    private Paper paper(){return Paper.builder().id(1L).userId(7L).build();}
    private PaperCollaborator collaborator(String role,String status){return PaperCollaborator.builder().paperId(1L).userId(8L).role(role).status(status).build();}
}
