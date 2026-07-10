package com.pdfchat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class AskRequest {

    @NotBlank(message = "Question is required")
    @NotNull(message = "Question is required")
    private String question;
}
