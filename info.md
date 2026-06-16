Recomendações práticas

Extrator: Apache PDFBox (extração por página).
Chunking: usar cada página como chunk inicial; se página for longa, fazer chunking por parágrafos/janelas de N sentenças mantendo metadados de página.
Armazenamento: in-memory List<PdfChunk>
Buscar: se quiser simplicidade: substring case-insensitive; se quiser semântico: OpenAI embeddings (ou outro provedor) + coseno, mas retornar sempre o texto original.

Exemplo mínimo (Java) — fluxo: extrair → indexar (lista de chunks) → endpoint de busca que retorna trechos originais

Dependência: org.apache.pdfbox:pdfbox

--------------

Resposta ao usuário (o que será devolvido)

Sempre devolva o texto original do chunk selecionado, mais metadados:
arquivo (nome)
número da página

-----------------

Core

LangChain4j: orquestra chains, retrievals e fluxos com LLM/embeddings.
Apache PDFBox: extrai texto dos PDFs (por página/posição).
Chunker: divide texto em blocos (página/parágrafo/janela) e adiciona metadados (arquivo, página).
Provedor de embeddings: gera vetores para cada chunk (ex.: OpenAI, Hugging Face).
Vector store / índice: armazena embeddings e permite busca por similaridade (ex.: FAISS, Milvus, Pinecone, Weaviate, ou in‑memory para protótipo).
Retriever: componente que consulta o vector store e retorna os chunks mais relevantes (sem modificar o texto original).

Backend / API

Spring Boot (ou outro framework Java): expõe endpoints REST para indexação, busca e gerenciamento de PDFs.
Armazenamento de arquivos: pasta local (pdfs) ou S3 para guardar os PDFs originais.
Banco de metadados: SQLite/Postgres para mapear chunks → arquivo → página e controlar estados de indexação.
Frontend

Interface web (React/Vue/HTML): campo de busca e exibição dos trechos retornados com arquivo e página.
Infra e utilitários