package com.invoice.dto;

import com.invoice.model.InvoiceInfo;
import lombok.Data;
import java.util.List;

@Data
public class TaskStatusResponse {
    private String taskId;
    private String status;
    private Integer progress;
    private Integer totalInvoices;
    private List<InvoiceInfo> invoices;
    private String createdAt;
    private String completedAt;
}

