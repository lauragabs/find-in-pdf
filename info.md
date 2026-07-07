to do 

Lematização/stemming em PT-BR para o grifo
Fazer herda, herdar, herdou, herdando virarem a mesma raiz antes de comparar.

Separar termos de intenção vs termos de conteúdo
Em quem herda, o conteúdo é herda/herdar; quem é intenção da pergunta.

Re-ranker de precisão no top resultados
Depois da busca semântica, reordenar top N com um modelo mais preciso (cross-encoder) ou regra híbrida com peso maior para termo técnico.

Chunking mais semântico
Trocar chunks longos por parágrafos menores com sobreposição leve. Isso reduz “vazamento de assunto” no trecho mostrado.

Score explicável no retorno
Retornar score semântico, termos-chave encontrados e quantos termos bateram. Ajuda muito a depurar relevância.

Conjunto de testes de consulta
Criar um mini benchmark de perguntas reais (herança, polimorfismo, classe abstrata, exceção...) com página esperada para medir evolução.