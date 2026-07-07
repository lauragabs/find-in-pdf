package com.br.edu.iftm.findinpdf.service;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    private static final double THRESHOLD_SEMANTICO = 0.35;
    private static final double THRESHOLD_MELHOR_RESULTADO = 0.20;
    private static final double THRESHOLD_FALLBACK_UNICO_TERMO = 0.30;
    private static final double BONUS_POR_TERMO_CHAVE = 0.12;
    private static final Set<String> TERMOS_GENERICOS_CONSULTA = Set.of(
            "buscar", "busca", "pesquisar", "pesquisa", "procurar", "procura", "consulta", "query");
    private static final Set<String> STOPWORDS_CONSULTA = Set.of(
            "a", "o", "as", "os", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
            "para", "por", "com", "sem", "como", "que", "e", "ou", "um", "uma", "uns", "umas",
            "qual", "quais", "quem", "quando", "onde", "porque", "oq", "oque",
            "porquê", "praque", "funciona", "funcionar", "sobre", "ser", "estar", "foi", "era", "sao", "são");

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
            if (indexarPdf(nomeArquivo.trim())) {
                sucesso = true;
            }
        }

        return sucesso;
    }

    private boolean indexarPdf(String nomeArquivo) {
        File pastaPdfs = encontrarPastaPdfs();
        File arquivoPdf = new File(pastaPdfs, nomeArquivo);

        if (!arquivoPdf.exists()) {
            System.err.println("Erro: Arquivo não encontrado em: " + arquivoPdf.getAbsolutePath());
            return false;
        }

        try (PDDocument documento = PDDocument.load(arquivoPdf)) {
            PDFTextStripper extrator = new PDFTextStripper();
            extrator.setSortByPosition(true);
            extrator.setLineSeparator("\n");
            extrator.setParagraphStart("");
            extrator.setParagraphEnd("\n\n");
            int totalPaginas = documento.getNumberOfPages();

            for (int paginaAtual = 1; paginaAtual <= totalPaginas; paginaAtual++) {
                extrator.setStartPage(paginaAtual);
                extrator.setEndPage(paginaAtual);
                String textoDaPagina = extrator.getText(documento).trim();

                if (!textoDaPagina.isEmpty()) {
                    String textoLimpo = limparTextoExtraido(textoDaPagina);
                    List<String> partes = criarChunks(textoLimpo);
                    for (String textoChunk : partes) {
                        PdfChunk novoChunk = new PdfChunk(nomeArquivo, paginaAtual, textoChunk);
                        novoChunk.setId((long) bancoDeDadosLocal.size());
                        novoChunk.setConteudoPagina(textoLimpo);
                        
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
            String parag = parte.trim().replaceAll("\\r?\\n", "\\n");
            if (!parag.isEmpty()) {
                paragrafos.add(parag);
            }
        }

        if (paragrafos.isEmpty() && !texto.trim().isEmpty()) {
            paragrafos.add(texto.trim().replaceAll("\\r?\\n", "\\n"));
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

    //buscar o significado da pergunta do usuário comparando com os chunks indexados usando IA
    public List<PdfChunk> buscar(String perguntaUsuario) {
        List<PdfChunk> resultados = new ArrayList<>();

        if (perguntaUsuario == null || perguntaUsuario.trim().isEmpty() || bancoDeDadosLocal.isEmpty()) {
            return resultados;
        }

        if (isConsultaGenerica(perguntaUsuario)) {
            System.out.println("[BUSCA] consulta genérica ignorada: '" + perguntaUsuario + "'");
            return resultados;
        }

        System.out.println("Buscando por significado para: '" + perguntaUsuario + "'");

        List<String> termosConsulta = extrairTermosAnalisados(perguntaUsuario);
        Set<String> termosConsultaNormalizados = termosConsulta.stream()
            .map(this::normalizarToken)
            .filter(t -> !t.isBlank())
            .collect(Collectors.toSet());
        Set<String> termosChaveConsulta = extrairTermosChaveConsulta(termosConsultaNormalizados);

        if (termosChaveConsulta.isEmpty()) {
            System.out.println("[BUSCA] consulta sem termos-chave após limpeza: '" + perguntaUsuario + "'");
            return resultados;
        }

        // Converte a pergunta do usuário em um vetor numérico usando o modelo de IA
        Embedding vetorPergunta = modeloIa.embed(perguntaUsuario.trim()).content();
        Map<PdfChunk, Double> pontuacaoFinalPorChunk = new HashMap<>();
        Map<PdfChunk, Integer> correspondenciasPorChunk = new HashMap<>();

        double melhorPontuacao = -1;
        PdfChunk melhorChunk = null;

        // Calcula a similaridade semântica entre a pergunta e cada chunk indexado
        for (PdfChunk chunk : bancoDeDadosLocal) {
            double pontuacaoSemantica = CosineSimilarity.between(chunk.getEmbedding(), vetorPergunta);
            int correspondencias = contarTermosChaveCorrespondentes(chunk, termosChaveConsulta);
            double pontuacaoFinal = pontuacaoSemantica + (correspondencias * BONUS_POR_TERMO_CHAVE);

            pontuacaoFinalPorChunk.put(chunk, pontuacaoFinal);
            correspondenciasPorChunk.put(chunk, correspondencias);

            if (pontuacaoFinal > melhorPontuacao) {
                melhorPontuacao = pontuacaoFinal;
                melhorChunk = chunk;
            }

            if (pontuacaoFinal > THRESHOLD_SEMANTICO) {
                resultados.add(chunk);
            }
        }

        if (!resultados.isEmpty()) {
            prepararPalavrasRelevantes(resultados, termosChaveConsulta);
            resultados = resultados.stream()
                    .filter(chunk -> chunk.getPalavrasRelevantes() != null && !chunk.getPalavrasRelevantes().isEmpty())
                    .collect(Collectors.toList());

            if (resultados.isEmpty()) {
                System.out.println("[BUSCA] candidatos sem termos relevantes após filtro textual.");
                return resultados;
            }

            resultados.sort(Comparator.comparingDouble(pontuacaoFinalPorChunk::get).reversed());
            System.out.println("[BUSCA] candidatos acima do threshold: " + resultados.size());
            return resultados;
        }

        // Se nenhum resultado forte for encontrado, retorna o chunk com a melhor pontuação
        int correspondenciasMelhorChunk = correspondenciasPorChunk.getOrDefault(melhorChunk, 0);
        boolean consultaUnicoTermo = termosChaveConsulta.size() == 1;
        boolean fallbackSemMatchTextual = consultaUnicoTermo
                && melhorPontuacao >= THRESHOLD_FALLBACK_UNICO_TERMO;

        if (melhorChunk != null
            && melhorPontuacao >= THRESHOLD_MELHOR_RESULTADO
                && (correspondenciasMelhorChunk > 0 || fallbackSemMatchTextual)) {
            System.out.println("Nenhuma correspondência forte encontrada; retornando o melhor resultado disponível com score=" + melhorPontuacao);
            melhorChunk.setPalavrasRelevantes(extrairPalavrasRelevantes(melhorChunk, termosChaveConsulta));
            if (correspondenciasMelhorChunk > 0
                    && melhorChunk.getPalavrasRelevantes() != null
                    && !melhorChunk.getPalavrasRelevantes().isEmpty()) {
                resultados.add(melhorChunk);
                System.out.println("[BUSCA] fallback melhor semântico aplicado.");
            } else if (fallbackSemMatchTextual) {
                resultados.add(melhorChunk);
                System.out.println("[BUSCA] fallback semântico para consulta de termo único aplicado.");
            } else {
                System.out.println("[BUSCA] fallback descartado por ausência de termos relevantes.");
            }
        } else {
            System.out.println("Nenhuma correspondência encontrada com score semântico confiável. Melhor score=" + melhorPontuacao);
            System.out.println("[BUSCA] nenhum resultado retornado.");
        }

        return resultados;
    }

    // Verifica se a consulta do usuário é genérica, contendo apenas termos comuns de busca
    private boolean isConsultaGenerica(String perguntaUsuario) {
        List<String> termos = extrairTermosAnalisados(perguntaUsuario);
        if (termos.isEmpty()) {
            return true;
        }

        return termos.stream().allMatch(TERMOS_GENERICOS_CONSULTA::contains);
    }

    // Conta quantos termos-chave da consulta estão presentes no chunk, considerando normalização e stopwords
    private int contarTermosChaveCorrespondentes(PdfChunk chunk, Set<String> termosConsultaNormalizados) {
        if (termosConsultaNormalizados.isEmpty()) {
            return 0;
        }

        String textoFonte = (chunk.getConteudoPagina() == null || chunk.getConteudoPagina().isBlank())
                ? chunk.getConteudoTexto()
                : chunk.getConteudoPagina();

        Set<String> termosDoChunk = extrairPalavrasOriginais(textoFonte).stream()
                .map(this::normalizarToken)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());

        int correspondencias = 0;
        for (String termoConsulta : termosConsultaNormalizados) {
            if (termosDoChunk.contains(termoConsulta)) {
                correspondencias++;
            }
        }

        return correspondencias;
    }

    // Extrai os termos-chave da consulta, removendo stopwords e termos muito curtos
    private Set<String> extrairTermosChaveConsulta(Set<String> termosConsultaNormalizados) {
        return termosConsultaNormalizados.stream()
                .filter(t -> t.length() > 2)
                .filter(t -> !STOPWORDS_CONSULTA.contains(t))
                .collect(Collectors.toSet());
    }

    // Normaliza o texto removendo acentos, convertendo para minúsculas e removendo espaços extras
    private String normalizarToken(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .trim();
    }

    // Prepara a lista de palavras relevantes para cada chunk com base nos termos-chave da consulta
    private void prepararPalavrasRelevantes(List<PdfChunk> chunks, Set<String> termosConsulta) {
        for (PdfChunk chunk : chunks) {
            chunk.setPalavrasRelevantes(extrairPalavrasRelevantes(chunk, termosConsulta));
        }
    }

    // Extrai as palavras relevantes de um chunk com base nos termos-chave da consulta
    private List<String> extrairPalavrasRelevantes(PdfChunk chunk, Set<String> termosConsulta) {
        LinkedHashSet<String> palavrasRelevantes = new LinkedHashSet<>();
        Set<String> termosConsultaNormalizados = termosConsulta.stream()
                .map(this::normalizarToken)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());

        if (termosConsultaNormalizados.isEmpty()) {
            return List.of();
        }

        String textoFonte = (chunk.getConteudoPagina() == null || chunk.getConteudoPagina().isBlank())
                ? chunk.getConteudoTexto()
                : chunk.getConteudoPagina();

        for (String termo : extrairPalavrasOriginais(textoFonte)) {
            String termoNormalizado = normalizarToken(termo);
            boolean corresponde = !termoNormalizado.isBlank() && termosConsultaNormalizados.contains(termoNormalizado);
            if (corresponde) {
                palavrasRelevantes.add(termo);
            }
        }

        return new ArrayList<>(palavrasRelevantes);
    }

    // Extrai os termos analisados de um texto, removendo stopwords e termos muito curtos
    private List<String> extrairTermosAnalisados(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        return Arrays.stream(texto.toLowerCase().split("[^\\p{L}\\p{Nd}]+"))
                .map(String::trim)
                .filter(p -> p.length() > 1)
                .distinct()
                .collect(Collectors.toList());
    }

    // Extrai as palavras originais de um texto, sem normalização
    private List<String> extrairPalavrasOriginais(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        return Arrays.stream(texto.split("[^\\p{L}\\p{Nd}]+"))
                .map(String::trim)
                .filter(p -> p.length() > 1)
                .distinct()
                .collect(Collectors.toList());
    }

    // Limpa o texto extraído do PDF, padronizando quebras de linha e removendo palavras quebradas por hífen
    private String limparTextoExtraido(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }

        String resultado = texto;

        // Padroniza quebras de linha
        resultado = resultado.replace("\r\n", "\n")
                            .replace("\r", "\n");

        // Remove palavras quebradas por hífen no fim da linha
        resultado = resultado.replaceAll("-\\s*\\n\\s*", "");

        // Caso o extrator esteja retornando "nn" no lugar de quebra de linha
        resultado = resultado.replace("nn", "\n\n");

        // Remove excesso de linhas em branco
        resultado = resultado.replaceAll("\\n{3,}", "\n\n");

        return resultado.trim();
    }
}
