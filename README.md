# FindInPDF

FindInPDF é uma aplicação web para indexar documentos PDF e buscar conteúdo por linguagem natural.

O sistema retorna os trechos mais relevantes, com arquivo e página de origem, e destaca no frontend os termos considerados relevantes pelo backend.

## Objetivo

Facilitar a localização de informação em PDFs extensos, reduzindo leitura manual e tempo de pesquisa.

## Funcionalidades

- Indexação de um ou mais PDFs da pasta `pdfs/`
- Busca híbrida (lexical e semântica)
- Expansão por sinônimos para consultas curtas
- Destaque de termos relevantes no resultado
- Exibição de trecho, arquivo e página

## Arquitetura de Busca

Atualmente a busca opera em três modos:

- Consulta com menos de 2 palavras: busca lexical com expansão de sinônimos (OpenThesaurus)
- Consulta com 2 palavras: modo híbrido (lexical + semântico)
- Consulta com mais de 2 palavras: prioridade para busca semântica (pergunta vs chunk)

### Componente lexical

- Lucene (`PortugueseAnalyzer`) para normalização e busca textual
- Índice em memória, reconstruído a cada indexação
- Query por termo exato e por prefixo

### Componente semântico

- Embeddings locais com `AllMiniLmL6V2EmbeddingModel`
- Similaridade de cosseno entre pergunta e chunks
- Fallback por melhor score quando aplicável

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

### Sinônimos (OpenThesaurus)

- `findinpdf.synonyms.enabled=true`
- `findinpdf.synonyms.openthesaurus.url=https://www.openthesaurus.pt/synonyme/search`
- `findinpdf.synonyms.openthesaurus.fallback-url=https://www.openthesaurus.de/synonyme/search`
- `findinpdf.synonyms.max-per-term=6`
- `findinpdf.synonyms.request-timeout-ms=1200`

## Logs de Busca

O backend emite logs com prefixo `[BUSCA]`, incluindo:

- termos analisados e termos originais
- query Lucene montada
- total de hits lexicais
- quantidade adicionada por complemento semântico
- total final retornado

## Referências de Conteúdo

- Java Básico e Orientação a Objeto: https://canal.cecierj.edu.br/012016/d7d8367338445d5a49b4d5a49f6ad2b9.pdf
- Spring Boot Reference: https://docs.spring.io/spring-boot/docs/3.2.7/reference/pdf/spring-boot-reference.pdf

## Design

Figma: https://www.figma.com/design/F4v5CwrHF31Pad7A056eQK/Untitled?node-id=0-1&t=PLNjFzH43Q4SL9rH-1

