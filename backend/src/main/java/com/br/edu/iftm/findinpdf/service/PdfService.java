package com.br.edu.iftm.findinpdf.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.br.edu.iftm.findinpdf.model.PdfChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

@Service
public class PdfService {

    private static final int MAX_RESULTADOS_BUSCA = 5;
    private static final double THRESHOLD_SEMANTICO = 0.45;
    private static final double THRESHOLD_MELHOR_RESULTADO = 0.25;
    private static final int DEFAULT_HTTP_TIMEOUT_MS = 1200;

    private final List<PdfChunk> bancoDeDadosLocal = new ArrayList<>();
    private final int maxWordsPerChunk;
    private final String chunkMode;
    private final Analyzer analyzer = new PortugueseAnalyzer();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final Map<String, List<String>> cacheSinonimos = new HashMap<>();
    private Directory diretorioIndice = new ByteBuffersDirectory();

    private final boolean synonymsEnabled;
    private final String openThesaurusPrimaryUrl;
    private final String openThesaurusFallbackUrl;
    private final int synonymsPerTerm;
    private final int synonymsTimeoutMs;
    
    // Inicializa o modelo de IA local 
    private final EmbeddingModel modeloIa = new AllMiniLmL6V2EmbeddingModel();

    public PdfService(
            @Value("${findinpdf.chunk.max-words:500}") int maxWordsPerChunk,
            @Value("${findinpdf.chunk.mode:words}") String chunkMode,
            @Value("${findinpdf.synonyms.enabled:true}") boolean synonymsEnabled,
            @Value("${findinpdf.synonyms.openthesaurus.url:https://www.openthesaurus.pt/synonyme/search}") String openThesaurusPrimaryUrl,
            @Value("${findinpdf.synonyms.openthesaurus.fallback-url:https://www.openthesaurus.de/synonyme/search}") String openThesaurusFallbackUrl,
            @Value("${findinpdf.synonyms.max-per-term:6}") int synonymsPerTerm,
            @Value("${findinpdf.synonyms.request-timeout-ms:1200}") int synonymsTimeoutMs) {
        this.maxWordsPerChunk = maxWordsPerChunk;
        this.chunkMode = chunkMode;
        this.synonymsEnabled = synonymsEnabled;
        this.openThesaurusPrimaryUrl = openThesaurusPrimaryUrl;
        this.openThesaurusFallbackUrl = openThesaurusFallbackUrl;
        this.synonymsPerTerm = Math.max(1, synonymsPerTerm);
        this.synonymsTimeoutMs = Math.max(200, synonymsTimeoutMs);
    }

    // Indexa múltiplos PDFs, mantendo todos os chunks indexados na memória
    public boolean indexarPdfs(List<String> nomesArquivos) {
        bancoDeDadosLocal.clear();
        boolean sucesso = false;

        reiniciarIndiceLucene();

        try (IndexWriter writer = criarIndexWriter(OpenMode.CREATE)) {
            for (String nomeArquivo : nomesArquivos) {
                if (nomeArquivo == null || nomeArquivo.trim().isEmpty()) {
                    continue;
                }
                if (indexarPdfSemLimpeza(nomeArquivo.trim(), writer)) {
                    sucesso = true;
                }
            }

            writer.commit();
            return sucesso;
        } catch (IOException e) {
            System.err.println("Erro ao preparar índice Lucene: " + e.getMessage());
            return false;
        }
    }

    private void reiniciarIndiceLucene() {
        try {
            diretorioIndice.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar índice Lucene anterior: " + e.getMessage());
        }
        diretorioIndice = new ByteBuffersDirectory();
    }

    // Método auxiliar para indexar um PDF sem limpar o índice anterior (usado para indexar múltiplos arquivos)
    private boolean indexarPdfSemLimpeza(String nomeArquivo, IndexWriter writer) {
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
                        adicionarChunkAoIndice(writer, novoChunk);
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

        System.out.println("Buscando por significado para: '" + perguntaUsuario + "'");

        List<String> termosConsulta = extrairTermosAnalisados(perguntaUsuario);
        List<String> termosOriginaisConsulta = extrairTermosOriginaisConsulta(perguntaUsuario);
        int quantidadePalavras = contarPalavras(perguntaUsuario);
        System.out.println("[BUSCA] termos analisados: " + termosConsulta);
        System.out.println("[BUSCA] termos originais: " + termosOriginaisConsulta);
        System.out.println("[BUSCA] quantidade de palavras da pergunta: " + quantidadePalavras);

        List<String> termosLexicais = new ArrayList<>(termosConsulta);
        if (quantidadePalavras < 2) {
            List<String> termosExpandidosOriginais = expandirTermosComSinonimos(termosOriginaisConsulta);
            termosLexicais = analisarListaTermos(termosExpandidosOriginais);
            System.out.println("[BUSCA] modo ativo: lexical com sinonimos (OpenThesaurus)");
            System.out.println("[BUSCA] termos lexicais expandidos (originais): " + termosExpandidosOriginais);
            System.out.println("[BUSCA] termos lexicais expandidos (analisados): " + termosLexicais);
        } else if (quantidadePalavras == 2) {
            System.out.println("[BUSCA] modo ativo: híbrido (lexical + semântico)");
        } else {
            System.out.println("[BUSCA] modo ativo: semântico (pergunta vs chunk)");
        }

        List<PdfChunk> resultadosLucene = quantidadePalavras > 2 ? new ArrayList<>() : buscarComLucene(termosLexicais);
        int qtdLuceneInicial = resultadosLucene.size();
        System.out.println("[BUSCA] hits Lucene: " + qtdLuceneInicial + " (limite=" + MAX_RESULTADOS_BUSCA + ")");

        if (resultadosLucene.size() >= MAX_RESULTADOS_BUSCA) {
            System.out.println("[BUSCA] retorno final via Lucene (sem complemento semântico). total=" + resultadosLucene.size());
            return resultadosLucene;
        }

        String perguntaLimpa = String.join(" ", termosConsulta);

        Embedding vetorPergunta = modeloIa.embed(perguntaLimpa.isEmpty() ? perguntaUsuario : perguntaLimpa).content(); // Converte a pergunta do usuário em um vetor numérico usando o modelo de IA
        Map<PdfChunk, Double> chunkSimilaridade = new HashMap<>();

        double melhorPontuacao = -1;
        PdfChunk melhorChunk = null;

        // Calcula a similaridade semântica entre a pergunta do usuário e cada chunk indexado usando o modelo de IA
        for (PdfChunk chunk : bancoDeDadosLocal) {
            double pontuacaoSemantica = CosineSimilarity.between(chunk.getEmbedding(), vetorPergunta);
            chunkSimilaridade.put(chunk, pontuacaoSemantica);

            if (pontuacaoSemantica > melhorPontuacao) {
                melhorPontuacao = pontuacaoSemantica;
                melhorChunk = chunk;
            }

            if (pontuacaoSemantica > THRESHOLD_SEMANTICO) {
                resultados.add(chunk);
            }
        }

        if (!resultados.isEmpty()) {
            resultados.sort(Comparator.comparingDouble(chunkSimilaridade::get).reversed());
            prepararPalavrasRelevantes(resultados, new LinkedHashSet<>(termosConsulta));
            System.out.println("[BUSCA] candidatos semânticos acima do threshold: " + resultados.size());

            LinkedHashSet<Long> idsJaSelecionados = resultadosLucene.stream()
                    .map(PdfChunk::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            int qtdAdicionadosSemantico = 0;

            for (PdfChunk chunk : resultados) {
                if (chunk.getId() == null || idsJaSelecionados.contains(chunk.getId())) {
                    continue;
                }

                resultadosLucene.add(chunk);
                idsJaSelecionados.add(chunk.getId());
                qtdAdicionadosSemantico++;

                if (resultadosLucene.size() >= MAX_RESULTADOS_BUSCA) {
                    break;
                }
            }

            System.out.println("[BUSCA] complemento semântico adicionado: " + qtdAdicionadosSemantico);
            System.out.println("[BUSCA] total final retornado: " + resultadosLucene.size());

            if (!resultadosLucene.isEmpty()) {
                return resultadosLucene;
            }

            return resultados;
        }

        // Se nenhum resultado semântico forte ou baseado em palavras-chave for encontrado, retorna o chunk com a melhor pontuação de similaridade, mesmo que seja baixa, para fornecer algum contexto ao usuário
        if (melhorChunk != null && melhorPontuacao >= THRESHOLD_MELHOR_RESULTADO) {
            System.out.println("Nenhuma correspondência forte ou baseada em palavras-chave encontrada; retornando o melhor resultado disponível com score=" + melhorPontuacao);
            melhorChunk.setPalavrasRelevantes(extrairPalavrasRelevantes(melhorChunk, new LinkedHashSet<>(termosConsulta)));
            boolean jaExiste = false;
            for (PdfChunk chunkExistente : resultadosLucene) {
                if (chunkExistente.getId() != null && chunkExistente.getId().equals(melhorChunk.getId())) {
                    jaExiste = true;
                    break;
                }
            }

            if (!jaExiste) {
                resultadosLucene.add(melhorChunk);
            }
            System.out.println("[BUSCA] fallback melhor semântico aplicado. total final=" + resultadosLucene.size());
        } else {
            System.out.println("Nenhuma correspondência encontrada com score semântico confiável. Melhor score=" + melhorPontuacao);
            System.out.println("[BUSCA] nenhum resultado retornado.");
        }

        return resultadosLucene;
    }

    private IndexWriter criarIndexWriter(OpenMode openMode) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(openMode);
        return new IndexWriter(diretorioIndice, config);
    }

    private void adicionarChunkAoIndice(IndexWriter writer, PdfChunk chunk) throws IOException {
        Document documento = new Document();
        documento.add(new StoredField("chunkId", String.valueOf(chunk.getId())));
        documento.add(new TextField("conteudoTexto", chunk.getConteudoTexto(), Field.Store.NO));
        writer.addDocument(documento);
    }

    private List<PdfChunk> buscarComLucene(List<String> termosConsulta) {
        if (termosConsulta.isEmpty()) {
            System.out.println("[BUSCA] Lucene sem termos válidos após analyzer.");
            return List.of();
        }

        try {
            if (!DirectoryReader.indexExists(diretorioIndice)) {
                System.out.println("[BUSCA] índice Lucene ainda não existe (indexe um PDF primeiro).");
                return List.of();
            }

            Query consulta = criarConsultaLucene(termosConsulta);
            System.out.println("[BUSCA] query Lucene: " + consulta);
            try (DirectoryReader reader = DirectoryReader.open(diretorioIndice)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs topDocs = searcher.search(consulta, MAX_RESULTADOS_BUSCA);
                System.out.println("[BUSCA] totalHits Lucene: " + topDocs.totalHits.value);

                if (topDocs.scoreDocs.length == 0) {
                    return List.of();
                }

                Set<String> termosConsultaSet = new LinkedHashSet<>(termosConsulta);
                List<PdfChunk> resultados = new ArrayList<>();

                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document documento = searcher.storedFields().document(scoreDoc.doc);
                    PdfChunk chunk = encontrarChunkPorId(documento.get("chunkId"));
                    if (chunk == null) {
                        continue;
                    }

                    chunk.setPalavrasRelevantes(extrairPalavrasRelevantes(chunk, termosConsultaSet));
                    resultados.add(chunk);
                }

                return resultados;
            }
        } catch (IOException e) {
            System.err.println("Erro ao consultar índice Lucene: " + e.getMessage());
            return List.of();
        }
    }

    private Query criarConsultaLucene(List<String> termosConsulta) {
        if (termosConsulta.isEmpty()) {
            return new MatchNoDocsQuery();
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String termo : termosConsulta) {
            builder.add(new TermQuery(new Term("conteudoTexto", termo)), BooleanClause.Occur.SHOULD);
            builder.add(new PrefixQuery(new Term("conteudoTexto", termo)), BooleanClause.Occur.SHOULD);
        }

        return builder.build();
    }

    private int contarPalavras(String perguntaUsuario) {
        if (perguntaUsuario == null || perguntaUsuario.isBlank()) {
            return 0;
        }

        return (int) Arrays.stream(perguntaUsuario.trim().split("\\s+"))
                .filter(p -> !p.isBlank())
                .count();
    }

    private List<String> expandirTermosComSinonimos(List<String> termosBase) {
        LinkedHashSet<String> termosExpandidos = new LinkedHashSet<>(termosBase);

        if (!synonymsEnabled || termosBase.isEmpty()) {
            System.out.println("[BUSCA] expansão de sinônimos desabilitada ou sem termos base.");
            return new ArrayList<>(termosExpandidos);
        }

        Map<String, List<String>> sinonimosPorTermo = new HashMap<>();

        for (String termo : termosBase) {
            List<String> sinonimos = buscarSinonimosOpenThesaurus(termo);
            sinonimosPorTermo.put(termo, sinonimos);
            for (String sinonimo : sinonimos) {
                termosExpandidos.add(sinonimo);
            }
        }

        System.out.println("[BUSCA] sinônimos aplicados por termo: " + sinonimosPorTermo);

        return new ArrayList<>(termosExpandidos);
    }

    private List<String> buscarSinonimosOpenThesaurus(String termo) {
        String chaveCache = termo.toLowerCase();
        if (cacheSinonimos.containsKey(chaveCache)) {
            System.out.println("[BUSCA] sinônimos via cache para '" + termo + "': " + cacheSinonimos.get(chaveCache));
            return cacheSinonimos.get(chaveCache);
        }

        List<String> sinonimos = buscarSinonimosEmUrl(openThesaurusPrimaryUrl, termo);
        if (sinonimos.isEmpty() && openThesaurusFallbackUrl != null && !openThesaurusFallbackUrl.isBlank()) {
            System.out.println("[BUSCA] OpenThesaurus PT indisponível/sem retorno; usando fallback de endpoint.");
            sinonimos = buscarSinonimosEmUrl(openThesaurusFallbackUrl, termo);
        }

        System.out.println("[BUSCA] sinônimos consultados para '" + termo + "': " + sinonimos);

        cacheSinonimos.put(chaveCache, sinonimos);
        return sinonimos;
    }

    private List<String> buscarSinonimosEmUrl(String baseUrl, String termo) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return List.of();
        }

        try {
            String encoded = URLEncoder.encode(termo, StandardCharsets.UTF_8);
            String url = baseUrl + "?q=" + encoded + "&format=application/json";

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(DEFAULT_HTTP_TIMEOUT_MS, synonymsTimeoutMs)))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode synsets = root.path("synsets");
            if (!synsets.isArray()) {
                return List.of();
            }

            LinkedHashSet<String> sinonimos = new LinkedHashSet<>();
            Set<String> termosBaseAnalisados = new LinkedHashSet<>(extrairTermosAnalisados(termo));
            for (JsonNode synset : synsets) {
                JsonNode terms = synset.path("terms");
                if (!terms.isArray()) {
                    continue;
                }

                for (JsonNode termNode : terms) {
                    String valor = termNode.path("term").asText("").trim();
                    if (valor.isEmpty()) {
                        continue;
                    }

                    if (valor.equalsIgnoreCase(termo)) {
                        continue;
                    }

                    for (String token : extrairTermosAnalisados(valor)) {
                        if (!termosBaseAnalisados.contains(token)) {
                            sinonimos.add(valor.toLowerCase());
                            if (sinonimos.size() >= synonymsPerTerm) {
                                return new ArrayList<>(sinonimos);
                            }
                        }
                    }
                }
            }

            return new ArrayList<>(sinonimos);
        } catch (Exception e) {
            System.err.println("[BUSCA] Erro ao consultar OpenThesaurus: " + e.getMessage());
            return List.of();
        }
    }

    private PdfChunk encontrarChunkPorId(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            return null;
        }

        int indice = Integer.parseInt(chunkId);
        if (indice < 0 || indice >= bancoDeDadosLocal.size()) {
            return null;
        }

        return bancoDeDadosLocal.get(indice);
    }

    private List<String> extrairTermosOriginaisConsulta(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        return Arrays.stream(texto.toLowerCase().split("[^\\p{L}\\p{Nd}]+"))
                .map(String::trim)
                .filter(p -> p.length() > 1)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> analisarListaTermos(List<String> termos) {
        LinkedHashSet<String> analisados = new LinkedHashSet<>();
        for (String termo : termos) {
            analisados.addAll(extrairTermosAnalisados(termo));
        }
        return new ArrayList<>(analisados);
    }

    private void prepararPalavrasRelevantes(List<PdfChunk> chunks, Set<String> termosConsulta) {
        for (PdfChunk chunk : chunks) {
            chunk.setPalavrasRelevantes(extrairPalavrasRelevantes(chunk, termosConsulta));
        }
    }

    private List<String> extrairPalavrasRelevantes(PdfChunk chunk, Set<String> termosConsulta) {
        LinkedHashSet<String> palavrasRelevantes = new LinkedHashSet<>();
        String textoFonte = (chunk.getConteudoPagina() == null || chunk.getConteudoPagina().isBlank())
                ? chunk.getConteudoTexto()
                : chunk.getConteudoPagina();

        for (String termo : extrairPalavrasOriginais(textoFonte)) {
            List<String> termosAnalisados = extrairTermosAnalisados(termo);
            boolean corresponde = termosAnalisados.stream().anyMatch(termosConsulta::contains);
            if (corresponde) {
                palavrasRelevantes.add(termo);
            }
        }

        return new ArrayList<>(palavrasRelevantes);
    }

    private List<String> extrairTermosAnalisados(String texto) {
        LinkedHashSet<String> termos = new LinkedHashSet<>();

        try (TokenStream tokenStream = analyzer.tokenStream("conteudoTexto", texto)) {
            CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                String termo = termAttribute.toString().trim();
                if (!termo.isEmpty()) {
                    termos.add(termo);
                }
            }

            tokenStream.end();
        } catch (IOException e) {
            System.err.println("Erro ao analisar texto com Lucene: " + e.getMessage());
        }

        return new ArrayList<>(termos);
    }

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
