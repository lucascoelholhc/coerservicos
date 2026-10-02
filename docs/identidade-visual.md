# Identidade visual: COE Serviços ("Dia carimbado")

> Leia antes de qualquer tarefa de front. A referência viva é o protótipo HTML "Dia carimbado" (commit `ef7d397`, `css/estilos.css`). O front React reproduz essas telas e estes tokens; **não redesenhar sem pedir.**

## 1. Conceito
Na COE cada dia de trabalho é pago antes, fica guardado e só vai para o profissional quando o cliente aprova. **Aprovar é carimbar o dia.**
- A cor da marca é a tinta azul-violeta de carimbo.
- O amarelo de sinalização de obra aparece **só no que pede atenção**.
- Uma única família tipográfica, Archivo, expandida nos títulos.

O elemento que só a COE tem é a **cartela de diárias**: cada dia é uma casa, com número grande e um carimbo ("Guardado", "Aprovado", "Liberado").

## 2. Marca
- **Símbolo:** casinha enxaimel (telhado + vigas do Vale do Itajaí) com porta amarela. SVG em `SIMBOLO()` no protótipo (`js/componentes.js`), viewBox `0 0 64 64`.
  - Fundo claro: traço `#3B3DC4`, parede `#FFFFFF`, porta `#FFCF33`.
  - Fundo escuro: traço `#FFFFFF`, telhado `#FFCF33`, parede `#1C1F2E`.
- **Logotipo:** símbolo de 38 px + "**COE**" (Archivo 800, largura 125%, 1.45rem) com "serviços" embaixo (500, .78rem, `--tinta-2`).
- Não distorcer, não trocar as cores do símbolo, não colocar sobre foto sem fundo sólido.

## 3. Cores
Sempre por variável CSS (no React: um `tokens.css` importado uma vez). **Nunca hex solto em componente.**

| Token | Valor | Uso |
|---|---|---|
| `--carimbo` | `#3B3DC4` | Marca, ação principal, selecionado, foco |
| `--carimbo-2` | `#2D2F9F` | Hover da ação principal |
| `--carimbo-3` | `#5254D6` | Variação clara (ilustrações) |
| `--carimbo-soft` | `#E4E5FA` | Fundo de item ativo, nota informativa, chip removível |
| `--carimbo-txt` | `#2D2F9F` | Texto azul sobre fundo claro |
| `--sinal` | `#FFCF33` | **Só atenção:** "Aprove o dia", badge de não lido, CTA em fundo escuro |
| `--sinal-2` | `#F5BE0B` | Hover do amarelo |
| `--sinal-soft` | `#FFF3C7` | Fundo de aviso |
| `--tinta` | `#1C1F2E` | Texto principal; botão secundário escuro |
| `--tinta-2` | `#4A4F63` | Texto de apoio |
| `--tinta-3` | `#6C7186` | Texto terciário (dia ocupado, desabilitado) |
| `--papel` | `#EEF0F4` | Fundo da página |
| `--surface` | `#FFFFFF` | Cartões, cabeçalho, menu inferior |
| `--surface-2` / `--surface-3` | `#E6E8EE` / `#D9DCE5` | Superfícies neutras, chips, hover |
| `--line` / `--line-2` | `#D9DCE5` / `#A9AEBF` | Bordas, tracejados ("picote") |
| `--ok` / `--ok-soft` | `#1E7A4C` / `#DDF0E5` | Liberado, livre, sucesso |
| `--bad` / `--bad-soft` | `#C2362C` / `#FBE2DF` | Erro, reclamação, perigo |
| `--warn` / `--warn-soft` | `#7A5300` / `#FFF3C7` | Em andamento, atenção |

Regras:
- **Sobre amarelo, o texto é sempre `--tinta`** (contraste 11:1), nunca branco.
- Branco sobre `--carimbo`: 7,9:1. Pode usar em qualquer tamanho.
- Cor de status é semântica (ok/bad/warn) e não substitui a cor da marca.
- **Ajuste pendente de contraste:** texto `--ok` sobre `--ok-soft` (4,48:1) e `--bad` sobre `--bad-soft` (4,42:1) ficam um pouco abaixo de 4,5:1 nas etiquetas pequenas. No React, use `#17633D` (6,1:1) e `#A52A21` (5,8:1) para o texto dessas etiquetas.
- Modo escuro: **A DEFINIR**. Hoje o protótipo é só claro (`color-scheme: light`).

## 4. Tipografia
Uma família só: **Archivo** (Google Fonts, eixos de largura e peso):
```html
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:ital,wdth,wght@0,100..125,400..800;1,100,400&display=swap">
```
Fallback: `"Segoe UI", Roboto, Arial, sans-serif`.

| Papel | Estilo |
|---|---|
| Títulos (h1–h4), números grandes | Archivo 800, `font-stretch: 125%`, `letter-spacing: -.015em`, `line-height: 1.02`, `text-wrap: balance` |
| h1 | `clamp(2rem, 6vw, 3.25rem)`; no início, até 4rem |
| h2 | `clamp(1.45rem, 3.6vw, 2.1rem)` |
| h3 | 1.25rem |
| Corpo | Archivo 400, **17 px** (18 px na área do profissional), `line-height: 1.55` |
| Rótulos | 600, .9–.95rem, **frase normal** (sem caixa alta espaçada) |
| Valores em R$ | 800 expandido, `font-variant-numeric: tabular-nums`, sem quebra de linha |

Caixa alta só dentro do carimbo.

## 5. Forma e espaço
- Raio: `--r: 12px` (cartões), `--r-sm: 6px` (botões, campos, dias do calendário). Etiquetas e chips: 999px.
- Conteúdo: largura máxima 1160 px; margem lateral 16 px no celular, 28 px a partir de 720 px.
- Sombra: praticamente nenhuma. A separação vem de borda de 1 px, fundo `--papel` e o tracejado "picote".
- Espaços em múltiplos de 2/4 px; grupos com `gap`, não margem.

## 6. Layout
- **Mobile-first a partir de 360 px.**
- Cabeçalho branco e fino (64 px), fixo no topo, com borda inferior.
- **Celular:** menu inferior fixo (66 px) com ícone + texto; item ativo com pílula `--carimbo-soft`.
- **A partir de 900 px:** o menu sobe para o cabeçalho e o menu inferior some.
- O conteúdo reserva espaço para o menu inferior e para a área segura do celular (`env(safe-area-inset-bottom)`).

## 7. Componentes

| Componente | Como é | Regras |
|---|---|---|
| **Botão** | Altura 52 px (lg 58, xl 68, sm 44), raio 6, peso 700 | Principal = `--carimbo` com texto branco. Secundário escuro = `--tinta`. Contorno = branco com borda `--tinta`. Suave = `--surface-2`. Perigo = contorno `--bad`. Sucesso = `--ok`. Em fundo escuro ou barra de ação, o principal vira amarelo com texto `--tinta`. **Um botão principal por tela.** |
| **Carimbo** | Borda 3 px, raio 8, texto 800 expandido em CAIXA ALTA, girado −8°, com leve falha de tinta (mask) | Só para estado de diária ou dinheiro: GUARDADO (azul), APROVADO (azul), LIBERADO (`.ok`), recusado (`.bad`). Anima "carimbar" uma vez; respeita `prefers-reduced-motion`. |
| **Etiqueta de status** (pill) | Pílula com ponto de cor + texto 600 .82rem | Sempre com texto; a cor nunca é a única pista. |
| **Nota** | Caixa com ícone + texto, fundo `*-soft` | azul = informação, amarelo = atenção, verde = ok, vermelho = erro. |
| **Chip** | Pílula `--surface-2`; removível em `--carimbo-soft` com ×, mínimo 38 px de altura | Filtros ativos e serviços. |
| **Cartela de diárias** | 3 casas lado a lado: dia da semana, número grande, carimbo, frase curta; rodapé com picote | Casa liberada com número verde; "aprove o dia" em amarelo; guardada com borda tracejada. |
| **Calendário** | Grade de 7 colunas, cada dia é uma casa quadrada (mínimo 44 px) com número 800 | Estados: livre (borda verde), ocupado (cinza riscado), folga/passado (tracejado), selecionado (`--carimbo`), trabalho marcado (`--tinta`), hoje (ponto). **Sempre com legenda.** |
| **Avatar** | Círculo com iniciais 800 expandidas, cor de fundo por pessoa | Sem foto real no lançamento. |
| **Cena / foto** | Ilustração 4:3 com legenda sobre fundo branco | Enquanto forem ilustrações, mostrar a etiqueta "Ilustração". |
| **Selo** | Ícone de escudo + "Documento conferido" em verde | Só aparece após a verificação do admin. "NR-10 conferido" para eletricista. |
| **Reputação** | Estrela + nota + nº de avaliações | Sem avaliações: "**Novo na COE**". **Nunca nota inventada.** |
| **Picote** | Linha tracejada de 2 px | Separa partes de um mesmo "documento" (cartela, resumo de pagamento). |

## 8. Nomes de status
O mesmo estado tem palavras diferentes para cada lado (fonte: `ST` e `ST_PRO` no protótipo).

| Estado (backend) | Cliente vê | Profissional vê | Cor |
|---|---|---|---|
| `agendada` | Agendada | Marcado | neutra |
| `paga` | Paga · guardada | Garantido | azul |
| `andamento` | Em andamento | Hoje | aviso |
| `aguardando` | Aprove o dia | Esperando o cliente | amarelo |
| `liberada` | Liberada | Pago | verde |
| `contestada` | Em análise | Reclamação | vermelho |
| `reembolsada` | Reembolsada | Devolvido ao cliente | neutra |
| `cancelada` | Cancelada | Cancelado | neutra |

`cancelada` ainda não tem texto no protótipo; a sugestão acima segue o mesmo padrão.

## 9. Acessibilidade (obrigatório)
- WCAG 2.1 AA. Contraste mínimo 4,5:1 em texto normal (ver o ajuste pendente na seção 3).
- Alvos de toque com **pelo menos 44 px**; botões principais com 52 px ou mais.
- Foco visível em tudo: `outline: 3px solid var(--carimbo); outline-offset: 2px`.
- Link "Pular para o conteúdo" no topo.
- Todo campo com `<label>`; erro escrito ao lado do campo, não só borda vermelha.
- Status nunca só por cor: sempre texto (etiqueta, carimbo, legenda do calendário).
- Ilustrações com `aria-hidden` ou descrição; a cartela do início tem `role="img"` com `aria-label` descritivo.
- `prefers-reduced-motion: reduce` desliga todas as animações.
- Navegação completa por teclado, inclusive no calendário e nos diálogos.

## 10. Tom dos textos
- **pt-BR simples, frases curtas, voz ativa.** O profissional pode ter pouca familiaridade com apps: escreva como se fala na obra.
- Palavras do dia a dia, não termos do sistema:
  - "guardado" e não "custódia";
  - "liberado" e não "repasse efetuado";
  - "reclamar" e não "abrir disputa";
  - "Cheguei" e "Terminei o dia" e não "check-in" e "concluir diária".
- Botão diz exatamente o que acontece: "Aprovar o dia", "Pagar R$ 616,00", "Mandar foto".
- Erro explica o que fazer: "Escreva seu celular com DDD", e não "Campo inválido".
- Valores sempre em reais com centavos (`R$ 280,00`); datas como "ter, 29/09".
- Sem emoji, sem exclamação em excesso, sem jargão de startup.
- Honestidade: ilustração marcada como "Ilustração", perfil novo como "Novo na COE", nada de número inventado.

## 11. Do protótipo para o React
- Tokens das seções 3 a 5 em `frontend/src/styles/tokens.css`. Os componentes usam só as variáveis.
- O protótipo mantém nomes antigos de classe que **não** devem ir para o React: `.btn-telha` = botão principal (azul carimbo), `.btn-azul` = botão escuro (`--tinta`), `--navy`/`--laranja` = aliases antigos. No React: `<Botao variante="principal" | "escuro" | "contorno" | "suave" | "perigo" | "sucesso">`.
- Componentes sugeridos: `Logo`, `Botao`, `Carimbo`, `StatusDiaria` (recebe o estado e o lado: cliente/profissional), `Nota`, `Chip`, `CartelaDias`, `Calendario`, `Avatar`, `Cena`, `Selo`, `Reputacao`, `MenuInferior`, `Cabecalho`.
- Ícones: os mesmos traços do protótipo (`ic()` em `js/util.js`), como componentes SVG com `aria-hidden`.
