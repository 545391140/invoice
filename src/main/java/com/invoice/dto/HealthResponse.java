package com.invoice.dto;

import lombok.Data;

@Data
public class HealthResponse {
    private String status;
    private String version;
    private String uptime;
}

