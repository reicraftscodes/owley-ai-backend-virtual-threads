package com.pdfchat.model;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class AskResponse {
    private String outputText;
    private String resultMessage;
    private Boolean result;
}
