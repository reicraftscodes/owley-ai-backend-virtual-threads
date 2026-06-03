package com.pdfchat.constants;

public class DocumentIngestionConstant {
    private DocumentIngestionConstant() {}

    public static final String QUESTION_REQUIRED_MESSAGE = "Question is required";
    public static final int MAX_PDF_LIMIT = 5;
    public static final String UPLOAD_DIR = System.getProperty("user.home") + "/pdf-chat-uploads";
    public static final String PDF_COUNT_CHECK = "Current PDF count in DB: {}";
    public static final String UPLOAD_DESCRIPTION_REJECTED = "Upload rejected — max {} PDFs already reached";
    public static final String UPLOAD_DESCRIPTION_ALLOWED = "Upload allowed — storing PDF in Cloudinary";
    public static final String CLOUDINARY_URL_RECEIVED = "Cloudinary URL received: {}";
    public static final String DOCUMENT_RECORD_SAVED = "Document record saved as PROCESSING — file: {}, uploadTime: {}";
    public static final String NO_TEXT_FOUND_MESSAGE = "No extractable text found in PDF";
    public static final String CHUNKS_INDEXED = "Indexed {} chunks for: {}";
    public static final String UPLOAD_RESULT_SUCCESS = "Success";
    public static final String VECTORS_CLEARED = "Cleared {} existing vectors";
    public static final int MAX_VECTOR_CLEAR_LIMIT = 10000;
    public static final String DEFAULT_FILENAME = "upload.pdf";
    public static final String FILENAME_SANITIZE_REGEX = "[^a-zA-Z0-9._-]";
    public static final String FILENAME_REPLACEMENT = "_";

}