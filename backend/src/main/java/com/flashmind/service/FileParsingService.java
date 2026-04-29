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
            throw new BusinessException("Tên file không hợp lệ");
        }

        String text;
        if (filename.toLowerCase().endsWith(".pdf")) {
            text = extractFromPdf(file);
        } else if (filename.toLowerCase().endsWith(".txt")) {
            text = extractFromText(file);
        } else {
            throw new BusinessException("Chỉ hỗ trợ file PDF và TXT");
        }

        if (text.isBlank()) {
            throw new BusinessException("File trống hoặc không thể đọc nội dung");
        }

        // Cắt bớt nếu quá dài (tránh tốn token OpenAI)
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
            throw new BusinessException("Không thể đọc file PDF");
        }
    }

    private String extractFromText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read text file", e);
            throw new BusinessException("Không thể đọc file text");
        }
    }
}
