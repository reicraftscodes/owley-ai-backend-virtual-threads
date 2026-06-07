package com.pdfchat.repository;


import com.pdfchat.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagesDocumentRepository extends JpaRepository<DocumentEntity, Long> {

}