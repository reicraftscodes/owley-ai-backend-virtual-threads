# Owley Policy Reader

Owley Policy Reader is a Spring Boot application for document intelligence. Upload PDFs or images, extract their content, and ask questions in natural language. Using RAG, Pinecone, and OpenAI, the application retrieves relevant document context to generate answers that are grounded in the uploaded content, reducing hallucinations and improving response accuracy.
Upload PDFs and images.

With Owley Policy Reader, you can:

- Upload PDFs and images.
- Extract text and useful information from your documents.
- Ask questions in plain English and get accurate AI answers.
- Use Pinecone to quickly search your documents.
- Use OpenAI to reason and give answers based only on the uploaded documents, so it won’t make things up.

This tool is great for reading policies, reports, contracts, or any documents with lots of text. It helps you turn documents into useful information fast.
## Prerequisites
- Java 21
- Pinecone API
- OpenAI API key
- Cloudinary account
- Maven
- MySQL

## Features

- Upload PDF documents and images with acceptable file type - PNG, JPG, JPEG, WEBP (max 5)
- Extract text from images using GPT-4o Vision OCR
- Ask questions about uploaded documents and images
- Finley AI answers in British English with a Gen Z personality
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
