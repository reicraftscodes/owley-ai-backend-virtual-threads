package com.pdfchat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskResponse {
    private String output_text;
    private List<String> sources;
    private String resultMessage;
    private Boolean result;
}
