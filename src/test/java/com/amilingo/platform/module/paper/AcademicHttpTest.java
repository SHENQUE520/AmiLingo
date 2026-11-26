package com.amilingo.platform.module.paper;
import com.amilingo.platform.common.config.security.AuthenticatedUser;
import com.amilingo.platform.module.paper.controller.*;
import com.amilingo.platform.module.paper.service.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import com.amilingo.platform.module.patent.controller.PatentController;
import com.amilingo.platform.module.patent.service.PatentService;
import com.amilingo.platform.module.patent.entity.Patent;
import com.amilingo.platform.module.patent.repository.*;
import com.amilingo.platform.module.research.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AcademicHttpTest {
    PatentRepository patentRepo; AcademicSecurity security; PatentService service; MockMvc mvc;
    @BeforeEach void setup(){
        patentRepo=mock(PatentRepository.class); service=mock(PatentService.class);
        security=new AcademicSecurity(mock(PaperAccessService.class),patentRepo,mock(ResearchProjectRepository.class),mock(ProjectMemberRepository.class),mock(ProjectDeliverableRepository.class),mock(PatentInventorRepository.class));
        mvc=MockMvcBuilders.standaloneSetup(proxy(new PatentController(service))).setControllerAdvice(new AcademicHttpErrors()).build();
    }
    Object proxy(Object target){var factory=new AspectJProxyFactory(target);factory.setProxyTargetClass(true);factory.addAspect(security);return factory.getProxy();}
    void login(long id){var u=AuthenticatedUser.builder().userId(id).username("test").role("USER").build();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void anonymousIs401() throws Exception {mvc.perform(get("/api/patent").param("userId","1")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @Test void forgedIdentityIs403() throws Exception {login(1);mvc.perform(get("/api/patent").param("userId","2")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void otherOwnersObjectIs403() throws Exception {login(1);Patent p=new Patent();p.setUserId(2L);when(patentRepo.findById(9L)).thenReturn(Optional.of(p));mvc.perform(delete("/api/patent/9")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void ownerCanList() throws Exception {login(1);when(service.listByUser(1L)).thenReturn(List.of());mvc.perform(get("/api/patent").param("userId","1")).andExpect(status().isOk());verify(service).listByUser(1L);}
    @Test void forgedBodyIsRejected() throws Exception {login(1);mvc.perform(post("/api/patent").contentType("application/json").content("{\"userId\":2,\"title\":\"test\"}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void researchAndPaperRequireLogin() throws Exception {
        var rs=mock(com.amilingo.platform.module.research.service.ResearchProjectService.class);
        var ps=mock(PaperService.class);
        mvc=MockMvcBuilders.standaloneSetup(proxy(new com.amilingo.platform.module.research.controller.ResearchProjectController(rs)),proxy(new PaperController(ps))).setControllerAdvice(new AcademicHttpErrors()).build();
        mvc.perform(get("/api/research").param("userId","1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/paper").param("userId","1")).andExpect(status().isUnauthorized());
        login(1);
        mvc.perform(get("/api/research").param("userId","2")).andExpect(status().isForbidden());
        mvc.perform(get("/api/paper").param("userId","2")).andExpect(status().isForbidden());
        verifyNoInteractions(rs,ps);
    }
    @Test void fileRoundTripAndDeletedFile() throws Exception {
        login(1);var blobs=mock(AcademicFileRepository.class);var attachments=mock(PaperAttachmentRepository.class);
        Map<String,AcademicFile> saved=new HashMap<>(); final PaperAttachment[] record={null};
        when(blobs.save(any())).thenAnswer(i->{AcademicFile f=i.getArgument(0);saved.put(f.getId(),f);return f;});
        when(blobs.findById(anyString())).thenAnswer(i->Optional.ofNullable(saved.get(i.getArgument(0))));
        when(attachments.save(any())).thenAnswer(i->{record[0]=i.getArgument(0);record[0].setId(7L);return record[0];});
        when(attachments.findById(7L)).thenAnswer(i->Optional.of(record[0]));
        mvc=MockMvcBuilders.standaloneSetup(proxy(new AcademicFileController(security,blobs,attachments,mock(PatentDocumentRepository.class),mock(ProjectDeliverableRepository.class)))).setControllerAdvice(new AcademicHttpErrors()).build();
        byte[] bytes="research sample".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/paper/3/files").file(new MockMultipartFile("file","../sample.txt","text/plain",bytes))).andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("sample.txt"));
        String id=saved.keySet().iterator().next();
        mvc.perform(get("/api/paper/3/files/"+id)).andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/paper/4/files/"+id)).andExpect(status().isNotFound());
        record[0].setStatus("DELETED");mvc.perform(get("/api/paper/3/files/"+id)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/paper/3/files").file(new MockMultipartFile("file",new byte[0]))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/paper/3/files").file(new MockMultipartFile("file",new byte[1024*1024+1]))).andExpect(status().isBadRequest());
        clear();mvc.perform(get("/api/paper/3/files/"+id)).andExpect(status().isUnauthorized());
    }
}
