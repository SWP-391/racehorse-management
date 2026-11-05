package vn.racehorse.incident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_photo")
public class IncidentPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] content;

    protected IncidentPhoto() {
    }

    public IncidentPhoto(Long reportId, String fileName, String contentType, byte[] content) {
        this.reportId = reportId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
