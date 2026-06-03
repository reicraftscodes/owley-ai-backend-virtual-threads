package com.pdfchat.service;

import com.pdfchat.model.AskResponse;

public interface RagService {
    AskResponse ask(String question);
}
