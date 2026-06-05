# Owley AI (Backend Development)

Owley AI is a Spring Boot application for analysing policy documents. Users can upload PDFs or images, extract their content, and ask questions in natural language. Using RAG, Pinecone, and OpenAI, the application retrieves relevant document context to generate answers grounded in the uploaded content, reducing hallucinations and improving response accuracy. Supports PDF and image uploads.


With Owley Policy Reader, you can:

- Upload PDFs and images.
- Extract text and useful information from your documents.
- Ask questions in plain English and get accurate AI answers.
- Use Pinecone to quickly search your documents.
- Use OpenAI to reason and give answers based only on the uploaded documents, so it won’t make things up.

This tool is great for reading policy documents with large amounts of text. It helps you quickly turn documents into useful information.

To see the **UI frontend** for this project, see [owley-ai-frontend](https://github.com/reicraftscodes/owley-ai-frontend) repository.

## Prerequisites
- Java 21 + Virtual Threads
- Pinecone API
- OpenAI API key
- Cloudinary account
- Maven
- MySQL

## Features
- Upload PDF documents and images with acceptable file type - PNG, JPG, JPEG, WEBP (max 5 per request)
- Extract text from images using GPT-4o Vision OCR
- Ask questions in natural language and get instant AI-powered answers.
- Secure file storage with Cloudinary (authenticated access only)
- Vector search powered by Pinecone
- Document records persisted in MySQL

---
### API Documentation

### Upload PDF
```bash
curl --location 'http://localhost:8081/api/documents/upload/pdf' \
--form 'pdf=@"/path/to/your.pdf"'
```

### Upload single images or multiple images
```bash
curl --location 'http://localhost:8081/api/documents/upload/images' \
--form 'files=@"/path/to/image1.png"' \
--form 'files=@"/path/to/image2.jpg"'
```

### Ask a Question
```bash
curl --location 'http://localhost:8081/api/documents/ask' \
--header 'Content-Type: application/json' \
--data '{"question": "What is this document about?"}'
```

--- 

## License
This project is licensed under the MIT License
