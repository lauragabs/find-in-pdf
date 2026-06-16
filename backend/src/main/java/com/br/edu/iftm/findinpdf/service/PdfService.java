package com.br.edu.iftm.findinpdf.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.br.edu.iftm.findinpdf.model.PdfChunk;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

@Service
public class PdfService {

    private final List<PdfChunk> bancoDeDadosLocal = new ArrayList<>();
    private final int maxWordsPerChunk;
    private final String chunkMode;
    
    // Inicializa o modelo de IA local 
    private final EmbeddingModel modeloIa = new AllMiniLmL6V2EmbeddingModel();

    public PdfService(
            @Value("${findinpdf.chunk.max-words:500}") int maxWordsPerChunk,
            @Value("${findinpdf.chunk.mode:words}") String chunkMode) {
        this.maxWordsPerChunk = maxWordsPerChunk;
        this.chunkMode = chunkMode;
    }

    // Indexa múltiplos PDFs, mantendo todos os chunks indexados na memória
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
                    List<String> partes = criarChunks(textoDaPagina);
                    for (String textoChunk : partes) {
                        PdfChunk novoChunk = new PdfChunk(nomeArquivo, paginaAtual, textoChunk);
                        
                        // Transforma o texto do chunk em um vetor numérico
                        Embedding vetorSignificado = modeloIa.embed(textoChunk).content();
                        novoChunk.setEmbedding(vetorSignificado);

                        bancoDeDadosLocal.add(novoChunk);
                    }
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

    // Cria chunks de texto com base no modo configurado (parágrafo ou número máximo de palavras)
    private List<String> criarChunks(String texto) {
        if ("paragraph".equalsIgnoreCase(chunkMode)) {
            return dividirTextoEmParagrafos(texto);
        }
        return dividirTextoEmChunks(texto, maxWordsPerChunk);
    }

    // Divide o texto em parágrafos usando quebras de linha duplas como delimitadores
    private List<String> dividirTextoEmParagrafos(String texto) {
        List<String> paragrafos = new ArrayList<>();
        String[] partes = texto.split("\\r?\\n\\s*\\r?\\n");

        for (String parte : partes) {
            String parag = parte.trim().replaceAll("\\r?\\n", " ");
            if (!parag.isEmpty()) {
                paragrafos.add(parag);
            }
        }

        if (paragrafos.isEmpty() && !texto.trim().isEmpty()) {
            paragrafos.add(texto.trim().replaceAll("\\r?\\n", " "));
        }

        return paragrafos;
    }

    // Divide o texto em chunks com base no número máximo de palavras
    private List<String> dividirTextoEmChunks(String texto, int maxWords) {
        List<String> chunks = new ArrayList<>();
        String[] palavras = texto.split("\\s+");

        if (palavras.length == 0) {
            return chunks;
        }

        StringBuilder builder = new StringBuilder();
        int contador = 0;

        for (String palavra : palavras) {
            if (contador > 0) {
                builder.append(' ');
            }
            builder.append(palavra);
            contador++;

            if (contador >= maxWords) {
                chunks.add(builder.toString().trim());
                builder.setLength(0);
                contador = 0;
            }
        }

        if (builder.length() > 0) {
            chunks.add(builder.toString().trim());
        }

        return chunks;
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

    // palavras comuns que não agregam significado e podem ser ignoradas na busca para melhorar a relevância dos resultados
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "com", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "para", "por", "e", "ou", "um", "uma", "uns", "umas", "que", "o", "a"
    ));

    //buscar o significado da pergunta do usuário comparando com os chunks indexados usando IA
    public List<PdfChunk> buscar(String perguntaUsuario) {
        List<PdfChunk> resultados = new ArrayList<>();

        if (perguntaUsuario == null || perguntaUsuario.trim().isEmpty() || bancoDeDadosLocal.isEmpty()) {
            return resultados;
        }

        System.out.println("Buscando por significado para: '" + perguntaUsuario + "'");

        // Extrai palavras-chave da pergunta do usuário, ignorando stop words e palavras muito curtas
        List<String> palavrasChave = Arrays.stream(perguntaUsuario.toLowerCase().split("[^\\p{L}\\p{Nd}]+"))
                .filter(p -> p.length() > 1 && !STOP_WORDS.contains(p))
                .distinct()
                .collect(Collectors.toList());

        Embedding vetorPergunta = modeloIa.embed(perguntaUsuario).content(); // Converte a pergunta do usuário em um vetor numérico usando o modelo de IA
        Map<PdfChunk, Double> chunkSimilaridade = new HashMap<>();

        double melhorPontuacao = -1;
        PdfChunk melhorChunk = null;
        double threshold = 0.45; //sensibilidade para considerar um resultado relevante(quanto menor, mais resultados serão retornados, mesmo os menos relevantes)

        // Calcula a similaridade semântica entre a pergunta do usuário e cada chunk indexado usando o modelo de IA
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

        if (!resultados.isEmpty()) {
            resultados.sort(Comparator.comparingDouble(chunkSimilaridade::get).reversed());
            return resultados;
        }

        // Se nenhum resultado semântico forte for encontrado, tenta encontrar chunks que contenham as palavras-chave extraídas da pergunta do usuário
        if (!palavrasChave.isEmpty()) {
            List<PdfChunk> fallback = bancoDeDadosLocal.stream()
                    .filter(chunk -> chunkContemPalavraChave(chunk, palavrasChave))
                    .sorted((c1, c2) -> Double.compare(
                            chunkSimilaridade.getOrDefault(c2, -1.0),
                            chunkSimilaridade.getOrDefault(c1, -1.0)))
                    .limit(5)
                    .collect(Collectors.toList());

            if (!fallback.isEmpty()) {
                return fallback;
            }
        }

        //todo : se nenhum resultado semântico forte ou baseado em palavras-chave for encontrado, procura semanticas baseadas em cada palavra-chave individualmente e retorna os chunks mais relevantes para cada palavra-chave, mesmo que a pontuação de similaridade seja baixa, para fornecer algum contexto ao usuário. Isso pode ajudar a encontrar informações relevantes mesmo quando a pergunta do usuário é muito diferente dos textos indexados ou quando o modelo de IA não consegue captar a semântica corretamente.


        // Se nenhum resultado semântico forte ou baseado em palavras-chave for encontrado, retorna o chunk com a melhor pontuação de similaridade, mesmo que seja baixa, para fornecer algum contexto ao usuário
        if (melhorChunk != null) {
            System.out.println("Nenhuma correspondência forte ou baseada em palavras-chave encontrada; retornando o melhor resultado disponível com score=" + melhorPontuacao);
            resultados.add(melhorChunk);
        }

        return resultados;
    }

    // Verifica se o chunk contém alguma das palavras-chave extraídas da pergunta do usuário, considerando apenas o conteúdo do texto
    private boolean chunkContemPalavraChave(PdfChunk chunk, List<String> palavrasChave) {
        String textoChunk = chunk.getConteudoTexto().toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ");

        for (String palavra : palavrasChave) {
            if (textoChunk.contains(palavra)) {
                return true;
            }
        }

        return false;
    }
}
