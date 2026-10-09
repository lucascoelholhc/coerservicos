# Protótipo "Dia carimbado" (referência só de leitura)

- **Origem:** `C:\Users\luqlh\Downloads\coe-servicos-prototipo-v5\coe-servicos` (repositório git do protótipo).
- **Commit:** `ef7d397` (`ef7d39760d84aafccd600362b29a76a09e1f00b0`), "Redesign visual "Dia carimbado" e logo enxaimel", de 01/10/2026.
- **Cópia feita em:** 08/10/2026, os 14 arquivos byte a byte (conferidos por sha256 contra os blobs do commit; fim de linha CRLF original preservado pelo `.gitattributes`).

**Referência só de leitura: não editar.** Mudança no protótipo = nova cópia de outro commit, com este README atualizado. O protótipo fica fora do lint, do stylelint e de qualquer build do front; o React reproduz as telas e os tokens (ver `docs/identidade-visual.md`), sem importar nada daqui.

## Como abrir

Por HTTP (não por `file://`, que pode quebrar os scripts):

```bash
cd frontend && npm run prototipo          # depois do FE-01: http://localhost:4174
python -m http.server 4174 --directory docs/prototipo   # alternativa sem Node
```

- O Início é a tela do visitante (barra "Ver como: Visitante").
- A barra de controles no topo (cenário e papel) é só do protótipo: não vai para o React.
- O protótipo carrega a Archivo do Google Fonts (precisa de internet); o React usa a fonte hospedada no próprio front.
