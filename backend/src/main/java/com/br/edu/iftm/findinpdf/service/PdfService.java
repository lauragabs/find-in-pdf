package com.br.edu.iftm.findinpdf.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.br.edu.iftm.findinpdf.model.PdfChunk;

@Service
public class PdfService {

    // Nosso "Banco de Dados" temporário local (em memória)
    private final List<PdfChunk> bancoDeDadosLocal = new ArrayList<>();

    /**
     * Lê um arquivo PDF da pasta local, extrai o texto página por página
     * e salva os pedaços (chunks) na memória.
     */
    public void indexarPdf(String nomeArquivo) {
        // Caminho para a pasta 'pdfs' na raiz do projeto
        File arquivoPdf = new File("pdfs/" + nomeArquivo);

        if (!arquivoPdf.exists()) {
            System.err.println("Erro: O arquivo " + nomeArquivo + " não foi encontrado na pasta 'pdfs/'.");
            return;
        }

        try (PDDocument documento = PDDocument.load(arquivoPdf)) {
            PDFTextStripper extrator = new PDFTextStripper();
            int totalPaginas = documento.getNumberOfPages();

            System.out.println("Iniciando a leitura do arquivo: " + nomeArquivo + " (" + totalPaginas + " páginas)");

            // Loop para ler o PDF página por página (O PDFBox começa na página 1)
            for (int paginaAtual = 1; paginaAtual <= totalPaginas; paginaAtual++) {
                // Configura o extrator para ler apenas a página atual do loop
                extrator.setStartPage(paginaAtual);
                extrator.setEndPage(paginaAtual);

                // Extrai o texto daquela página específica
                String textoDaPagina = extrator.getText(documento).trim();

                // Evita salvar páginas que estejam completamente vazias
                if (!textoDaPagina.isEmpty()) {
                    PdfChunk novoChunk = new PdfChunk(nomeArquivo, paginaAtual, textoDaPagina);
                    bancoDeDadosLocal.add(novoChunk);
                }
            }

            System.out.println("Indexação concluída com sucesso! Total de chunks na memória: " + bancoDeDadosLocal.size());

        } catch (IOException e) {
            System.err.println("Erro ao processar o arquivo PDF: " + e.getMessage());
        }
    }

    /**
     * Retorna todos os chunks armazenados na memória até o momento.
     */
    public List<PdfChunk> obterTodosOsChunks() {
        return this.bancoDeDadosLocal;
    }

    /**
     * Realiza uma busca textual simples (case-insensitive) nos chunks armazenados na memória.
     * * @param termoBusca O termo ou palavra que o usuário está procurando.
     * @return Uma lista de PdfChunk que contêm o termo pesquisado.
     * 
     *   atualmente funcionando como um "Ctrl+F"
     */
    public List<PdfChunk> buscar(String termoBusca) {
        List<PdfChunk> resultados = new ArrayList<>();

        // Se a busca estiver vazia ou o banco local estiver vazio, retorna lista vazia
        if (termoBusca == null || termoBusca.trim().isEmpty() || bancoDeDadosLocal.isEmpty()) {
            return resultados;
        }

        // Convertemos o termo de busca para minúsculo para a busca ser "case-insensitive"
        String termoMinusculo = termoBusca.toLowerCase();

        System.out.println("Iniciando busca local pelo termo: '" + termoBusca + "'");

        // Varre cada pedaço de PDF guardado na memória
        for (PdfChunk chunk : bancoDeDadosLocal) {
            String textoDoChunkMinusculo = chunk.getConteudoTexto().toLowerCase();

            // Verifica se o texto daquela página contém o que o usuário digitou
            if (textoDoChunkMinusculo.contains(termoMinusculo)) {
                resultados.add(chunk);
            }
        }

        System.out.println("Busca finalizada. Encontrados " + resultados.size() + " resultado(s).");
        return resultados;
    }
}