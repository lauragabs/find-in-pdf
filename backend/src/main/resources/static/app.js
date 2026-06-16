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

    output.textContent = `Buscando por '${pergunta}' ...`;

    try {
        const response = await fetch(`/api/pdfs/buscar?pergunta=${encodeURIComponent(pergunta)}`);
        const contentType = response.headers.get('content-type') || '';

        if (contentType.includes('application/json')) {
            const data = await response.json();
            output.textContent = JSON.stringify(data, null, 2);
            return;
        }

        const text = await response.text();
        output.textContent = text;
    } catch (error) {
        output.textContent = `Erro de conexão: ${error.message}`;
    }
});

carregarPdfs();
