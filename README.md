<p align="center">
  <img src="https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/spring%20boot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20AI-6DB33F?style=for-the-badge&logo=spring&logoColor=white" />
  <img src="https://img.shields.io/badge/OpenAI-412991?style=for-the-badge&logo=openai&logoColor=white" />
  <img src="https://img.shields.io/badge/Pinecone-000000?style=for-the-badge" />
  <img src="https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white" />
  <img src="https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white" />
  <img src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" />
  <img src="https://img.shields.io/badge/Cloudinary-3448C5?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Actuator-6DB33F?style=for-the-badge&logo=spring&logoColor=white" />
</p>

# Owley AI

Owley AI is a Spring Boot application designed to analyse policy documents using document retrieval and question answering.

Users can upload PDF files or images, extract text from documents, and ask questions in natural language. The application uses Retrieval-Augmented Generation (RAG), Pinecone, and OpenAI to retrieve relevant document content and generate accurate, context-aware responses based on the uploaded files. This approach helps reduce hallucinations and improve response reliability.

#### Business Requirements
Owley AI is designed to simplify the analysis of large policy documents.  The system is well suited for domains such as compliance, legal analysis, insurance, and enterprise documentation, where accuracy and contextual understanding are critical.



To see the **UI frontend** for this project, see [owley-ai-frontend](https://github.com/reicraftscodes/owley-ai-frontend) repository.


## Architecture Overview
Owley AI follows a Retrieval-Augmented Generation (RAG) workflow:

- Documents are uploaded and processed (PDF or image OCR)
- Text is extracted and split into manageable chunks
- Embeddings are generated and stored in Pinecone
- Relevant content is retrieved based on the user’s query
- Retrieved context is combined with prompts and sent to OpenAI
- A grounded, context-aware response is returned to the user

### Sequence Diagrams

The following diagrams illustrate the upload workflows in Owley AI:

- [PDF Upload Sequence Diagram](https://res.cloudinary.com/dvwxun4vh/image/upload/v1781189137/owley-pdf-sd_zlkbqy.png)
- [Image Upload Sequence Diagram](https://res.cloudinary.com/dvwxun4vh/image/upload/v1781189138/owley-img-sd_kwafyv.png)

**Prompt Design**

Owley AI uses structured prompts within its RAG pipeline to ensure responses are accurate, consistent, and grounded in document content.

After retrieving relevant content using Pinecone, the system combines this context with predefined prompts and sends it to OpenAI models. These prompts guide the model’s reasoning and control how answers are generated.
The prompt design ensures that the system:

- Uses only retrieved document content when answering questions 
- Applies OpenAI reasoning to interpret and explain information clearly 
- Simplifies complex or legal language for non-technical users 
- Avoids assumptions, external knowledge, or unsupported inputs (such as links)
- Provides a clear response when information is not available

This approach combines precise retrieval with controlled AI reasoning, resulting in reliable and easy-to-understand answers. Implementation details are available in _`PromptConstant.java`_.

## Features

- User Management (Admin and General Users)
- Upload PDF and image files (PNG, JPG, JPEG, WEBP) with a limit of 5 files per request 
- Extract text from images using GPT‑4o Vision OCR 
- Ask questions in natural language and receive instant, Owley AI responses 
- Store files securely via Cloudinary with authenticated access 
- Perform fast and relevant semantic search using Pinecone 
- Persist document metadata and records in MySQL

## Prerequisites
- Java 21 + Virtual Threads
- Pinecone API
- OpenAI API key
- Cloudinary account
- Maven
- MySQL

## Testing
This project is also being actively checked with SonarLint integrated into the developer's IDE. SonarQube analysis is performed automatically in the CI/CD pipeline, and it passes as long as there are no SonarQube violations.


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



## License
This project is licensed under the MIT License
