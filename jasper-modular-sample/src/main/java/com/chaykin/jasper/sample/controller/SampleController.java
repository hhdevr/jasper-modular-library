package com.chaykin.jasper.sample.controller;

import com.chaykin.jasper.core.exception.JasperModularException;
import com.chaykin.jasper.core.renderer.JasperModularRenderer;
import com.chaykin.jasper.sample.data.FinancialReportService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/sample")
public class SampleController {

    @GetMapping(value = "/financial", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> printModularReport() throws JasperModularException {
        byte[] pdf = new JasperModularRenderer<>().render(FinancialReportService.buildModule());

        return ResponseEntity.ok()
                             .header(HttpHeaders.CONTENT_DISPOSITION,
                                     "attachment; filename=financial_report.pdf")
                             .body(new ByteArrayResource(pdf));
    }
}