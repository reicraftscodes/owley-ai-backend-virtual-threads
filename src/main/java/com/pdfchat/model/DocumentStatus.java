package com.pdfchat.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentStatus {

    PROCESSING("Document is being processed"),
    READY("Document is ready for use"),
    FAILED("Document processing failed");

    private final String description;
}