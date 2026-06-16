package com.br.edu.iftm.findinpdf.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.edu.iftm.findinpdf.model.PdfChunk;
import com.br.edu.iftm.findinpdf.service.PdfService;

@RestController
@RequestMapping("/api/pdfs")
@CrossOrigin(origins = "*") // Permite que o seu Frontend (React/Vue) consulte o Backend sem erros de CORS
public class PdfController {

    private final PdfService pdfService;

    // O Spring Boot injeta o nosso serviço automaticamente aqui
    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    /**
     * Endpoint para ler e indexar um arquivo PDF que está na pasta 'pdfs'.
     * URL para testar no navegador: http://localhost:8080/api/pdfs/indexar?arquivo=Como_Usar_o_Liquidificador_Manual.pdf
     */
    @GetMapping("/indexar")
    public ResponseEntity<String> indexar(@RequestParam String arquivo) {
        boolean sucesso = pdfService.indexarPdf(arquivo);

        if (!sucesso) {
            return ResponseEntity.badRequest().body("Erro: arquivo '" + arquivo + "' não encontrado ou não pôde ser processado.");
        }

        return ResponseEntity.ok("Arquivo '" + arquivo + "' indexado com sucesso na memória usando IA!");
    }

    /**
     * Endpoint para fazer a busca semântica inteligente.
     * URL para testar no navegador: http://localhost:8080/api/pdfs/buscar?pergunta=Como_montar?
     */
    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(@RequestParam String pergunta) {
        List<PdfChunk> resultados = pdfService.buscar(pergunta);

        if (resultados.isEmpty()) {
            return ResponseEntity.ok("Nenhum resultado encontrado para: '" + pergunta + "'");
        }

        return ResponseEntity.ok(resultados);
    }
}