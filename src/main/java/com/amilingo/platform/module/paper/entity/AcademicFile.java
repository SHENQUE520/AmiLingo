package com.amilingo.platform.module.paper.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="academic_file") @Getter @Setter @NoArgsConstructor
public class AcademicFile {
    @Id private String id;
    @Column(nullable=false) private String domain;
    @Column(nullable=false) private Long ownerId;
    private Long recordId;
    private String name;
    @Lob @Column(nullable=false, columnDefinition="LONGBLOB") private byte[] content;
}
