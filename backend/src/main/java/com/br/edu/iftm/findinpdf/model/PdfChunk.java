package com.br.edu.iftm.findinpdf.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.langchain4j.data.embedding.Embedding;

/**
 * Classe que representa um pedaço (chunk) de texto extraído de um PDF.
 * Contém o conteúdo textual e os metadados necessários para a localização.
 * Tamanho do chunk: definido por 'maxWordsPerChunk' no serviço, recomendado entre 300-500 palavras para melhor desempenho.
 * O campo 'embedding' é ignorado na serialização JSON para evitar exposição desnecessária
 */
public class PdfChunk {

    private Long id;
    private String nomeArquivo;
    private int numeroPagina;
    private String conteudoTexto;
    
    @JsonIgnore  
    private Embedding embedding;

    public PdfChunk() {
    }

    // Construtor completo para facilitar a criação
    public PdfChunk(String nomeArquivo, int numeroPagina, String conteudoTexto) {
        this.nomeArquivo = nomeArquivo;
        this.numeroPagina = numeroPagina;
        this.conteudoTexto = conteudoTexto;
    }

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public int getNumeroPagina() {
        return numeroPagina;
    }

    public void setNumeroPagina(int numeroPagina) {
        this.numeroPagina = numeroPagina;
    }

    public String getConteudoTexto() {
        return conteudoTexto;
    }

    public void setConteudoTexto(String conteudoTexto) {
        this.conteudoTexto = conteudoTexto;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }
}

