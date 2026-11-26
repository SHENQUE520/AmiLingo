package com.amilingo.platform.module.paper.controller;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import com.amilingo.platform.module.paper.service.AcademicSecurity;
import com.amilingo.platform.module.patent.entity.PatentDocument;
import com.amilingo.platform.module.patent.repository.PatentDocumentRepository;
import com.amilingo.platform.module.research.entity.ProjectDeliverable;
import com.amilingo.platform.module.research.repository.ProjectDeliverableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.nio.charset.StandardCharsets;

@RestController @RequiredArgsConstructor
public class AcademicFileController {
    private final AcademicSecurity security;
    private final AcademicFileRepository files;
    private final PaperAttachmentRepository papers;
    private final PatentDocumentRepository patents;
    private final ProjectDeliverableRepository research;

    @PostMapping("/api/{domain:paper|patent|research}/{ownerId}/files") @Transactional
    public Map<String,Object> upload(@PathVariable String domain, @PathVariable Long ownerId,
                                    @RequestParam("file") MultipartFile file) throws java.io.IOException {
        security.access(domain, ownerId, true);
        if(file.isEmpty() || file.getSize()>1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件大小须为 1 字节至 1 MiB");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("file.bin").replace('\\','/');
        name = name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
        if(name.isBlank() || name.length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件名无效");
        var blob=new AcademicFile(); blob.setId(UUID.randomUUID().toString()); blob.setDomain(domain); blob.setOwnerId(ownerId); blob.setName(name); blob.setContent(file.getBytes());
        String url="/api/"+domain+"/"+ownerId+"/files/"+blob.getId();
        long user=security.currentUser();
        if(domain.equals("paper")) {
            var a=papers.save(PaperAttachment.builder().paperId(ownerId).category("OTHER").fileName(name).contentType("application/octet-stream").fileSize(file.getSize()).storageKey(blob.getId()).fileUrl(url).uploadedBy(user).status("ACTIVE").versionNo(1).build()); blob.setRecordId(a.getId());
        } else if(domain.equals("patent")) {
            var a=patents.save(PatentDocument.builder().patentId(ownerId).category("OTHER").fileName(name).contentType("application/octet-stream").fileSize(file.getSize()).storageKey(blob.getId()).fileUrl(url).uploadedBy(user).status("ACTIVE").versionNo(1).build()); blob.setRecordId(a.getId());
        } else {
            var a=research.save(ProjectDeliverable.builder().projectId(ownerId).creatorUserId(user).type("OTHER").title(name).fileName(name).contentType("application/octet-stream").fileSize(file.getSize()).storageKey(blob.getId()).fileUrl(url).status("DRAFT").build()); blob.setRecordId(a.getId());
        }
        files.save(blob);
        return Map.of("id",blob.getId(),"recordId",blob.getRecordId(),"fileName",name,"fileSize",file.getSize(),"storageKey",blob.getId(),"fileUrl",url);
    }
    @GetMapping("/api/{domain:paper|patent|research}/{ownerId}/files/{fileId}") @Transactional(readOnly=true)
    public ResponseEntity<byte[]> download(@PathVariable String domain,@PathVariable Long ownerId,@PathVariable String fileId) {
        security.access(domain,ownerId,false);
        var f=files.findById(fileId).filter(x->domain.equals(x.getDomain()) && ownerId.equals(x.getOwnerId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean active=switch(domain) {
            case "paper" -> papers.findById(f.getRecordId()).filter(x->"ACTIVE".equals(x.getStatus()) && fileId.equals(x.getStorageKey())).isPresent();
            case "patent" -> patents.findById(f.getRecordId()).filter(x->"ACTIVE".equals(x.getStatus()) && fileId.equals(x.getStorageKey())).isPresent();
            default -> research.findById(f.getRecordId()).filter(x->!"DELETED".equals(x.getStatus()) && fileId.equals(x.getStorageKey())).isPresent();
        };
        if(!active)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("X-Content-Type-Options","nosniff").header("Cache-Control","no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.getName(),StandardCharsets.UTF_8).build().toString()).body(f.getContent());
    }
}
