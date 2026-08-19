package com.flashmind.service;

import com.flashmind.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class FileParsingService {

    private static final int MAX_TEXT_LENGTH = 8000;

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException("Invalid file name");
        }

        String text;
        if (filename.toLowerCase().endsWith(".pdf")) {
            text = extractFromPdf(file);
        } else if (filename.toLowerCase().endsWith(".txt")) {
            text = extractFromText(file);
        } else {
            throw new BusinessException("Only PDF and TXT files are supported");
        }

        if (text.isBlank()) {
            throw new BusinessException("The file is empty or its content could not be read");
        }

        // Truncate if too long, to cap Claude token spend
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
            log.info("Text truncated to {} characters", MAX_TEXT_LENGTH);
        }
        return text;
    }

    private String extractFromPdf(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("Failed to parse PDF", e);
            throw new BusinessException("Could not read the PDF file");
        }
    }

    private String extractFromText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read text file", e);
            throw new BusinessException("Could not read the text file");
        }
    }
}
