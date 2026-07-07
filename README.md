# FindInPDF

FindInPDF é uma aplicação web para indexar documentos PDF e buscar conteúdo por linguagem natural.

O sistema retorna os trechos mais relevantes, com arquivo e página de origem, e destaca no frontend os termos considerados relevantes pelo backend.

## Objetivo

Facilitar a localização de informação em PDFs extensos, reduzindo leitura manual e tempo de pesquisa.

## Funcionalidades

- Indexação de um ou mais PDFs da pasta `pdfs/`
- Busca semântica com embeddings locais
- Fallback controlado para melhorar cobertura em consultas curtas
- Destaque de termos relevantes no resultado
- Exibição de trecho, arquivo e página

## Arquitetura de Busca

Atualmente a busca é semântica, baseada em embeddings de chunks indexados em memória.

### Componente semântico

- Embeddings locais com `AllMiniLmL6V2EmbeddingModel`
- Similaridade de cosseno entre pergunta e chunks
- Termos-chave da pergunta passam por normalização (minúsculas, sem acento) e remoção de stopwords
- Pontuação final por chunk: score semântico + bônus por correspondência textual de termos-chave

### Regras de decisão de resultado

- Retorno direto: chunks com pontuação final acima de `0.35`
- Fallback geral: melhor chunk se score >= `0.20` e houver ao menos 1 correspondência textual
- Fallback especial (consulta de 1 termo): permite retorno sem correspondência textual quando score >= `0.30`
- Consultas genéricas (ex.: apenas "buscar", "pesquisar") são ignoradas

## Tecnologias

### Backend
- Java 17
- Spring Boot 3.1.5
- Maven
- Apache PDFBox
- Apache Lucene
- LangChain4j

### Frontend
- HTML
- CSS
- JavaScript (Vanilla)

Observação: os arquivos do frontend usados pela aplicação ficam em `backend/src/main/resources/static`.

## Estrutura do Projeto

```text
findinpdf/
├── backend/
│   ├── src/main/java/com/br/edu/iftm/findinpdf/
│   │   ├── Main.java
│   │   ├── controller/
│   │   │   └── PdfController.java
│   │   ├── model/
│   │   │   └── PdfChunk.java
│   │   └── service/
│   │       └── PdfService.java
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── static/
│   │       ├── index.html
│   │       ├── styles.css
│   │       └── app.js
│   └── pom.xml
├── frontend/
├── pdfs/
├── pom.xml
└── README.md
```

## Endpoints da API

- `GET /api/pdfs/listar`: lista os PDFs disponíveis
- `POST /api/pdfs/indexar`: indexa os PDFs selecionados (array JSON)
- `GET /api/pdfs/buscar?pergunta=...`: executa a busca
- `GET /api/health`: health-check simples da aplicação

## Como Executar

### Pré-requisitos

- Java 17+
- Maven 3.6+
- PDFs na pasta `pdfs/` na raiz do projeto

### Rodar backend

```bash
cd backend
mvn spring-boot:run
```

### Acessar aplicação

- Abra no navegador: `http://localhost:8080`

### Fluxo de uso

1. Selecione um ou mais PDFs
2. Clique em `Indexar selecionados`
3. Faça uma pergunta no campo de busca

## Configurações Principais

Arquivo: `backend/src/main/resources/application.properties`

### Chunking

- `findinpdf.chunk.mode=words` ou `paragraph`
- `findinpdf.chunk.max-words=500`

## Logs de Busca

O backend emite logs com prefixo `[BUSCA]`, incluindo:

- consultas genéricas ignoradas
- consulta sem termos-chave após limpeza
- quantidade de candidatos acima do threshold
- fallback semântico aplicado (inclusive para consulta de termo único)
- ausência de resultados quando nenhum critério de confiança é atendido

## Referências de Conteúdo

- Java Básico e Orientação a Objeto: https://canal.cecierj.edu.br/012016/d7d8367338445d5a49b4d5a49f6ad2b9.pdf

## Design

Figma: https://www.figma.com/design/F4v5CwrHF31Pad7A056eQK/Untitled?node-id=0-1&t=PLNjFzH43Q4SL9rH-1