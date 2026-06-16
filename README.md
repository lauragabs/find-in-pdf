# FindInPDF

FindInPDF é uma aplicação web desenvolvida para realizar buscas inteligentes em documentos PDF utilizando consultas em linguagem natural.

O sistema permite que o usuário faça perguntas sobre o conteúdo dos documentos, retornando os trechos relevantes encontrados, juntamente com o nome do arquivo e a página onde a informação está localizada.

A estrutura do projeto foi organizada para separar o backend e o frontend, e foi criada uma pasta `pdfs` para armazenar os documentos usados pelo sistema. Entre os PDFs disponibilizados, o material "Java Básico Orientação a Objeto" foi obtido de https://canal.cecierj.edu.br/012016/d7d8367338445d5a49b4d5a49f6ad2b9.pdf, e Spring Boot foi obtida de https://docs.spring.io/spring-boot/docs/3.2.7/reference/pdf/spring-boot-reference.pdf, e os demais arquivos foram gerados por IA.

## Design e Prototipagem

Figma: https://www.figma.com/design/F4v5CwrHF31Pad7A056eQK/Untitled?node-id=0-1&t=PLNjFzH43Q4SL9rH-1

## Objetivo

Facilitar a localização de informações em documentos PDF, reduzindo o tempo gasto na leitura e pesquisa manual de arquivos extensos.

## Funcionalidades

- Indexação do conteúdo dos documentos;
- Busca utilizando linguagem natural;
- Exibição dos resultados encontrados;
- Identificação do arquivo de origem;
- Exibição da página onde o trecho foi localizado.

## Tecnologias

### Backend
- **Java 17** - Linguagem de programação
- **Spring Boot 3.1.5** - Framework web para criar a API REST
- **Apache Maven 3.9.11** - Gerenciador de dependências e build
- **Apache PDFBox 2.0.27** - Extração de texto de arquivos PDF
- **LangChain4j 0.31.0** - Framework para integração com IA
- **AllMiniLmL6V2EmbeddingModel** - Modelo de embeddings local (CPU-based)
- **Tomcat** - Servidor HTTP embarcado (porta 8080)

### Frontend
- **HTML5** - Markup dos documentos
- **CSS3** - Estilização com variáveis CSS customizadas
- **JavaScript (Vanilla)** - Lógica interativa com Fetch API

## Estrutura do Projeto

```
findinpdf/
├── backend/                          # Aplicação Spring Boot
│   ├── src/main/java/.../findinpdf/
│   │   ├── Main.java                 # Entry point da aplicação
│   │   ├── controller/               # Endpoints REST
│   │   │   └── PdfController.java
│   │   ├── service/                  # Lógica de negócio
│   │   │   └── PdfService.java
│   │   └── model/                    # Modelos de dados
│   │       └── PdfChunk.java
│   └── pom.xml
├── frontend/                         # Interface do usuário
│   ├── index.html                    # Página principal
│   ├── styles.css                    # Estilos
│   ├── app.js                        # Lógica JavaScript
│   └── pom.xml
├── pdfs/                             # Pasta com documentos PDF
├── pom.xml                           # POM parent (multi-módulo)
└── README.md
```

## Endpoints da API

- `GET /api/pdfs/listar` - Lista todos os PDFs disponíveis
- `GET /api/pdfs/indexar?arquivo=nome.pdf` - Indexa um PDF específico
- `POST /api/pdfs/indexar-selecionados` - Indexa múltiplos PDFs (JSON array)
- `GET /api/pdfs/buscar?pergunta=sua_pergunta` - Realiza busca semântica

## Como executar

### Pré-requisitos
- Java 17 ou superior instalado
- Maven 3.6 ou superior instalado
- PDFs na pasta `pdfs/` na raiz do projeto

### Passos


### Usando a aplicação

1. **Indexar documentos**:
   - A página exibe a lista de PDFs disponíveis na pasta `pdfs/`
   - Selecione um ou mais PDFs usando as checkboxes
   - Clique em "Indexar selecionados" para processar os documentos
   - O sistema extrairá o texto e gerará embeddings semânticos

2. **Buscar**:
   - Após indexar, digite sua pergunta no campo "Buscar no PDF indexado"
   - Clique em "Buscar" ou pressione Enter
   - O sistema retornará os trechos mais relevantes com arquivo e página

## Detalhes Técnicos

- **Embedding**: Usa o modelo AllMiniLmL6V2, executado localmente sem dependência externa
- **Similaridade**: Calcula similaridade de cosseno entre vetores de embeddings
- **Limiar**: Retorna resultados com score > 0.25, ou o melhor match se nenhum ultrapassar o limiar
- **Índice**: Armazenado em memória (lista); limpo quando novos PDFs são indexados
- **CORS**: Habilitado globalmente para permitir requisições do frontend

