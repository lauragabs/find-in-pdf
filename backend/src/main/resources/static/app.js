const output = document.getElementById('output');
const pdfList = document.getElementById('pdf-list');
const selectAllButton = document.getElementById('select-all');
const clearSelectionButton = document.getElementById('clear-selection');
const indexSelectedButton = document.getElementById('index-selected');
const searchForm = document.getElementById('search-form');

let pdfCheckboxes = [];

async function carregarPdfs() {
    try {
        const response = await fetch('/api/pdfs/listar');
        const pdfs = await response.json();

        if (!Array.isArray(pdfs) || pdfs.length === 0) {
            pdfList.textContent = 'Nenhum PDF encontrado na pasta pdfs.';
            return;
        }

        pdfList.innerHTML = '';
        pdfCheckboxes = pdfs.map((pdf) => {
            const row = document.createElement('div');
            row.className = 'pdf-row';

            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = pdf;
            checkbox.id = `pdf-${pdf}`;

            const label = document.createElement('label');
            label.setAttribute('for', `pdf-${pdf}`);
            label.textContent = pdf;

            row.appendChild(checkbox);
            row.appendChild(label);
            pdfList.appendChild(row);
            return checkbox;
        });
    } catch (error) {
        pdfList.textContent = `Erro ao carregar PDFs: ${error.message}`;
    }
}

function getSelecionados() {
    return pdfCheckboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.value);
}

selectAllButton.addEventListener('click', () => {
    pdfCheckboxes.forEach((checkbox) => (checkbox.checked = true));
});

clearSelectionButton.addEventListener('click', () => {
    pdfCheckboxes.forEach((checkbox) => (checkbox.checked = false));
});

indexSelectedButton.addEventListener('click', async () => {
    const selecionados = getSelecionados();
    if (selecionados.length === 0) {
        output.textContent = 'Selecione ao menos um PDF para indexar.';
        return;
    }

    output.classList.remove('results-mode');
    output.textContent = `Indexando ${selecionados.length} PDF(s)...`;

    try {
        const response = await fetch('/api/pdfs/indexar', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(selecionados),
        });

        const text = await response.text();
        output.textContent = response.ok ? text : `Erro ao indexar: ${text}`;
    } catch (error) {
        output.textContent = `Erro de conexão: ${error.message}`;
    }
});

searchForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const pergunta = document.getElementById('search-query').value.trim();
    if (!pergunta) return;

    output.classList.remove('results-mode');
    output.textContent = `Buscando por '${pergunta}' ...`;

    try {
        const response = await fetch(`/api/pdfs/buscar?pergunta=${encodeURIComponent(pergunta)}`);
        const contentType = response.headers.get('content-type') || '';

        if (contentType.includes('application/json')) {
            const data = await response.json();
            if (Array.isArray(data)) {
                const palavrasChave = extrairPalavrasChave(pergunta);
                output.classList.add('results-mode');
                output.innerHTML = data.map((chunk) => {
                    const textoChunk = String(chunk.conteudoTexto || chunk.text || '');
                    const textoPagina = String(chunk.conteudoPagina || textoChunk);
                    const arquivo = String(chunk.nomeArquivo || chunk.fileName || '');
                    const pagina = chunk.numeroPagina || chunk.page || '';
                    const baseTrecho = textoChunk.trim() ? textoChunk : textoPagina;
                    const trechoPreview = gerarTrechoPreview(baseTrecho, palavrasChave, 420);
                    const temPaginaCompleta = textoPagina.trim().length > trechoPreview.trim().length + 20;
                    const textoChunkDestacado = destacarPalavras(trechoPreview, palavrasChave);
                    const textoPaginaDestacado = destacarPalavras(textoPagina, palavrasChave);
                    const painelId = `full-page-${arquivo}-${String(pagina)}`
                        .replace(/\s+/g, '-')
                        .replace(/[^a-zA-Z0-9_-]/g, '');

                    const blocoPaginaCompleta = temPaginaCompleta
                        ? `<div class="result-actions"><button type="button" class="toggle-page-btn" data-target="${painelId}">Ver página completa</button></div><div id="${painelId}" class="result-fullpage hidden"><div class="result-label">Página completa</div><div class="result-text result-fullpage-text">${textoPaginaDestacado}</div></div>`
                        : '';

                    return `<div class="search-result"><div class="result-meta">Arquivo: ${escapeHtml(arquivo)} | Página: ${escapeHtml(String(pagina))}</div><div class="result-label">Trecho encontrado</div><div class="result-text result-preview">${textoChunkDestacado}</div>${blocoPaginaCompleta}</div>`;
                }).join('');
                return;
            }

            output.classList.remove('results-mode');
            output.textContent = JSON.stringify(data, null, 2);
            return;
        }

        output.classList.remove('results-mode');
        const text = await response.text();
        output.textContent = text;
    } catch (error) {
        output.classList.remove('results-mode');
        output.textContent = `Erro de conexão: ${error.message}`;
    }
});

output.addEventListener('click', (event) => {
    const button = event.target.closest('.toggle-page-btn');
    if (!button) {
        return;
    }

    const targetId = button.getAttribute('data-target');
    const painel = document.getElementById(targetId);
    if (!painel) {
        return;
    }

    const ficouOculto = painel.classList.toggle('hidden');
    button.textContent = ficouOculto ? 'Ver página completa' : 'Ocultar página completa';
});

function extrairPalavrasChave(pergunta) {
    const stopWords = new Set(['com', 'de', 'do', 'da', 'dos', 'das', 'em', 'no', 'na', 'nos', 'nas', 'para', 'por', 'e', 'ou', 'um', 'uma', 'uns', 'umas', 'que', 'o', 'a']);
    return pergunta
        .toLowerCase()
        .split(/[^\p{L}\p{Nd}]/u)
        .filter(p => p.length > 1 && !stopWords.has(p))
        .filter((p, i, arr) => arr.indexOf(p) === i);
}

function destacarPalavras(texto, palavras) {
    let resultado = escapeHtml(texto);
    palavras.forEach((palavra) => {
        const segura = escapeRegex(palavra);
        const regex = new RegExp(`(^|[^\\p{L}\\p{Nd}])(${segura})(?=[^\\p{L}\\p{Nd}]|$)`, 'giu');
        resultado = resultado.replace(regex, '$1<mark class="highlight">$2</mark>');
    });
    return resultado;
}

function escapeRegex(texto) {
    return texto.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function gerarTrechoPreview(texto, palavras, tamanhoMaximo) {
    const textoLimpo = (texto || '').trim().replace(/\s+/g, ' ');
    if (!textoLimpo) {
        return '';
    }

    if (textoLimpo.length <= tamanhoMaximo) {
        return textoLimpo;
    }

    const textoLower = textoLimpo.toLowerCase();
    let indice = -1;

    for (const palavra of palavras) {
        const idx = textoLower.indexOf(String(palavra).toLowerCase());
        if (idx !== -1 && (indice === -1 || idx < indice)) {
            indice = idx;
        }
    }

    if (indice === -1) {
        indice = 0;
    }

    const metade = Math.floor(tamanhoMaximo / 2);
    let inicio = Math.max(0, indice - metade);
    let fim = Math.min(textoLimpo.length, inicio + tamanhoMaximo);

    if (fim >= textoLimpo.length) {
        inicio = Math.max(0, textoLimpo.length - tamanhoMaximo);
    }

    const trecho = textoLimpo.slice(inicio, fim).trim();
    const prefixo = inicio > 0 ? '... ' : '';
    const sufixo = fim < textoLimpo.length ? ' ...' : '';
    return `${prefixo}${trecho}${sufixo}`;
}

function escapeHtml(text) {
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

carregarPdfs();
