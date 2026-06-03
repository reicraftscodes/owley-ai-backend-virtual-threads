package com.pdfchat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
public class AskResponse {
    private String outputText;
    private List<String> sources;
    private String resultMessage;
    private Boolean result;
}
