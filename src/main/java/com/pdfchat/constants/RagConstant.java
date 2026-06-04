package com.pdfchat.constants;

public class RagConstant {
    private RagConstant() {}

    public static final String RESULT_SUCCESS = "Success";
    public static final int TOP_K = 5;
    public static final String CONTEXT_SEPARATOR = "\n\n---\n\n";
    public static final String SOURCE_UNKNOWN = "unknown";
    public static final String NO_DOCS_FOUND = "No relevant documents found for question: {}";
    public static final String META_SOURCE = "source";
    public static final String META_URL = "cloudinary_url";
    public static final String META_TYPE = "type";
}