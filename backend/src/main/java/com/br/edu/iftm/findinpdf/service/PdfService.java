package com.br.edu.iftm.findinpdf.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.br.edu.iftm.findinpdf.model.PdfChunk;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

@Service
public class PdfService {

    private final List<PdfChunk> bancoDeDadosLocal = new ArrayList<>();
    
    // Inicializa o modelo de IA local 
    private final EmbeddingModel modeloIa = new AllMiniLmL6V2EmbeddingModel();

    public boolean indexarPdf(String nomeArquivo) {
        // Limpa o índice anterior para manter somente o último arquivo indexado
        bancoDeDadosLocal.clear();

        // Caminho relativo ao diretório raiz do projeto
        File arquivoPdf = new File("../pdfs/" + nomeArquivo);

        if (!arquivoPdf.exists()) {
            System.err.println("Erro: Arquivo não encontrado em: " + arquivoPdf.getAbsolutePath());
            return false;
        }

        try (PDDocument documento = PDDocument.load(arquivoPdf)) {
            PDFTextStripper extrator = new PDFTextStripper();
            int totalPaginas = documento.getNumberOfPages();

            for (int paginaAtual = 1; paginaAtual <= totalPaginas; paginaAtual++) {
                extrator.setStartPage(paginaAtual);
                extrator.setEndPage(paginaAtual);
                String textoDaPagina = extrator.getText(documento).trim();

                if (!textoDaPagina.isEmpty()) {
                    PdfChunk novoChunk = new PdfChunk(nomeArquivo, paginaAtual, textoDaPagina);
                    
                    // PASSAGEM DA IA: Transforma o texto da página em um vetor numérico
                    Embedding vetorSignificado = modeloIa.embed(textoDaPagina).content();
                    novoChunk.setEmbedding(vetorSignificado);

                    bancoDeDadosLocal.add(novoChunk);
                }
            }
            System.out.println("Indexado com IA! Total de chunks: " + bancoDeDadosLocal.size());
            return true;
        } catch (IOException e) {
            System.err.println("Erro: " + e.getMessage());
            return false;
        }
    }

    /**
     * Realiza a Busca Semântica por linguagem natural usando Similaridade de Cosseno.
     */
    public List<PdfChunk> buscar(String perguntaUsuario) {
        List<PdfChunk> resultados = new ArrayList<>();

        if (perguntaUsuario == null || perguntaUsuario.trim().isEmpty() || bancoDeDadosLocal.isEmpty()) {
            return resultados;
        }

        System.out.println("Buscando por significado para: '" + perguntaUsuario + "'");

        Embedding vetorPergunta = modeloIa.embed(perguntaUsuario).content();
        Map<PdfChunk, Double> chunkSimilaridade = new HashMap<>();

        double melhorPontuacao = -1;
        PdfChunk melhorChunk = null;
        double threshold = 0.25;

        for (PdfChunk chunk : bancoDeDadosLocal) {
            double pontuacaoSemantica = CosineSimilarity.between(chunk.getEmbedding(), vetorPergunta);
            chunkSimilaridade.put(chunk, pontuacaoSemantica);

            if (pontuacaoSemantica > melhorPontuacao) {
                melhorPontuacao = pontuacaoSemantica;
                melhorChunk = chunk;
            }

            if (pontuacaoSemantica > threshold) {
                resultados.add(chunk);
            }
        }

        if (resultados.isEmpty() && melhorChunk != null) {
            System.out.println("Nenhuma correspondência forte encontrada; retornando melhor resultado com score=" + melhorPontuacao);
            resultados.add(melhorChunk);
        }

        resultados.sort(Comparator.comparingDouble(chunkSimilaridade::get).reversed());

        return resultados;
    }
}