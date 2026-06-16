package com.br.edu.iftm.findinpdf.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
        return indexarPdfSemLimpeza(nomeArquivo);
    }

    public boolean indexarPdfs(List<String> nomesArquivos) {
        bancoDeDadosLocal.clear();
        boolean sucesso = false;

        for (String nomeArquivo : nomesArquivos) {
            if (nomeArquivo == null || nomeArquivo.trim().isEmpty()) {
                continue;
            }
            if (indexarPdfSemLimpeza(nomeArquivo.trim())) {
                sucesso = true;
            }
        }

        return sucesso;
    }

    // Método auxiliar para indexar um PDF sem limpar o índice anterior (usado para indexar múltiplos arquivos)
    private boolean indexarPdfSemLimpeza(String nomeArquivo) {
        File pastaPdfs = encontrarPastaPdfs();
        File arquivoPdf = new File(pastaPdfs, nomeArquivo);

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
                    
                    //Transforma o texto da página em um vetor numérico
                    Embedding vetorSignificado = modeloIa.embed(textoDaPagina).content();
                    novoChunk.setEmbedding(vetorSignificado);

                    bancoDeDadosLocal.add(novoChunk);
                }
            }
            System.out.println(" Arquivo " + nomeArquivo + " indexado com IA! Total de chunks: " + bancoDeDadosLocal.size());
            return true;
        } catch (IOException e) {
            System.err.println("Erro: " + e.getMessage());
            return false;
        }
    }

    // Tenta encontrar a pasta 'pdfs' tanto no diretório atual quanto no diretório pai
    private File encontrarPastaPdfs() {
        File pasta = new File("pdfs");
        if (pasta.exists() && pasta.isDirectory()) {
            return pasta;
        }
        pasta = new File("../pdfs");
        if (pasta.exists() && pasta.isDirectory()) {
            return pasta;
        }
        return pasta;
    }


    // Lista os arquivos PDF disponíveis na pasta 'pdfs'
    public List<String> listarPdfs() {
        File pastaPdfs = encontrarPastaPdfs();
        List<String> pdfFiles = new ArrayList<>();

        if (pastaPdfs != null && pastaPdfs.exists() && pastaPdfs.isDirectory()) {
            File[] arquivos = pastaPdfs.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (arquivos != null) {
                Arrays.sort(arquivos);
                for (File arquivo : arquivos) {
                    pdfFiles.add(arquivo.getName());
                }
            }
        }

        return pdfFiles;
    }

    //buscar o significado da pergunta do usuário comparando com os chunks indexados usando IA
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
        double threshold = 0.25; //sensibilidade para considerar um resultado relevante(quanto menor, mais resultados serão retornados, mesmo os menos relevantes)

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