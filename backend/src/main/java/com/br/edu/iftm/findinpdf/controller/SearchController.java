package com.br.edu.iftm.findinpdf.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.edu.iftm.findinpdf.model.PdfChunk;
import com.br.edu.iftm.findinpdf.service.PdfService;

@RestController
@RequestMapping("/api")
public class SearchController {

    @Autowired
    private PdfService pdfService;

    @GetMapping("/health")
    public String health() {
        return "OK - Aplicação rodando!";
    }

    @PostMapping("/indexar")
    public String indexarPdf(@RequestParam String nomeArquivo) {
        pdfService.indexarPdf(nomeArquivo);
        return "PDF indexado com sucesso: " + nomeArquivo;
    }

    @GetMapping("/buscar")
    public List<PdfChunk> buscar(@RequestParam String pergunta) {
        return pdfService.buscar(pergunta);
    }

}
