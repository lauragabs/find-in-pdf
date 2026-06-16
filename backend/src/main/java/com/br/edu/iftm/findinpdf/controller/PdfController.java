package com.br.edu.iftm.findinpdf.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.edu.iftm.findinpdf.model.PdfChunk;
import com.br.edu.iftm.findinpdf.service.PdfService;

@RestController
@RequestMapping("/api/pdfs")
@CrossOrigin(origins = "*")
public class PdfController {

    private final PdfService pdfService;

    // O Spring Boot injeta o nosso serviço automaticamente aqui
    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    /**
     * Lista todos os PDFs disponíveis na pasta 'pdfs'.
     */
    @GetMapping("/listar")
    public List<String> listarPdfs() {
        return pdfService.listarPdfs();
    }

    /**
     * Endpoint para ler e indexar um arquivo PDF que está na pasta 'pdfs'.
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
     * Endpoint para indexar múltiplos arquivos PDF selecionados pelo usuário.
     */
    @PostMapping("/indexar-selecionados")
    public ResponseEntity<String> indexarSelecionados(@RequestBody List<String> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) {
            return ResponseEntity.badRequest().body("Nenhum arquivo selecionado.");
        }

        boolean sucesso = pdfService.indexarPdfs(arquivos);
        if (!sucesso) {
            return ResponseEntity.badRequest().body("Nenhum arquivo foi indexado. Verifique se os nomes estão corretos e existem na pasta pdfs.");
        }

        return ResponseEntity.ok("Arquivos indexados com sucesso: " + String.join(", ", arquivos));
    }


    /**
     * Endpoint para fazer a busca semântica inteligente.
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