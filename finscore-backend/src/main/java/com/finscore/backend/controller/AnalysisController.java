package com.finscore.backend.controller;

import com.finscore.backend.dto.AnalysisResponse;
import com.finscore.backend.exception.ApiExceptions.InvalidRequestException;
import com.finscore.backend.service.AnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
public class AnalysisController {
    private final AnalysisService service;
    public AnalysisController(AnalysisService service) { this.service = service; }

    @GetMapping({"/api/v1/health", "/api/analysis/health"})
    public Map<String,String> health() { return Map.of("status", "UP", "service", "FINScore Backend"); }

    @PostMapping(value={"/api/v1/analysis", "/api/analysis/upload"}, consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidRequestException("A PDF file is required.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".pdf")) throw new InvalidRequestException("Only PDF files are accepted.");
        if (file.getSize() > 25L * 1024 * 1024) throw new InvalidRequestException("The PDF exceeds the 25 MB upload limit.");
        return service.analyze(file);
    }
}
