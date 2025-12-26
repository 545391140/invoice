package com.invoice.dto;

import com.invoice.model.InvoiceInfo;
import lombok.Data;
import java.util.List;

@Data
public class InvoiceRecognizeResponse {
    private String taskId;
    private Integer totalInvoices;
    private List<InvoiceInfo> invoices;
    private Double processingTime;
    private String originalFileUrl;
}



