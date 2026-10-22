import { useId, type ReactNode } from 'react';

import { Icone } from '../../icones/Icone';
import estilos from './Opcoes.module.css';

export interface Opcao {
  valor: string;
  /** Nome da opção (o que o leitor de tela lê). */
  texto: string;
  /** Linha menor abaixo do texto (só no cartão). */
  detalhe?: string;
  /** Texto curto só na tela (ex.: "seg"); o leitor de tela lê o {@link texto}. */
  curto?: string;
  /** Desenho decorativo ao lado do texto (só no cartão). */
  imagem?: ReactNode;
}

interface OpcoesProps {
  legenda: ReactNode;
  nome: string;
  /** Uma só (radio) ou várias (checkbox). */
  tipo: 'unica' | 'varias';
  /** Pílula (padrão), cartão grande (em grade), lista (uma por linha, texto longo) ou dia da semana (7 em linha). */
  forma?: 'pilula' | 'cartao' | 'lista' | 'dia';
  opcoes: readonly Opcao[];
  selecionadas: readonly string[];
  aoMudar: (selecionadas: string[]) => void;
  /** Em "várias": chegou no máximo, as outras ficam desabilitadas. */
  maximo?: number;
  erro?: string;
  dica?: string;
  /** A legenda só para o leitor de tela (quando o título da tela já é a pergunta). */
  legendaOculta?: boolean;
}

/**
 * Escolha por toque (protótipo: .opt, .bigopt e .dias7): o input nativo fica por baixo, então teclado,
 * leitor de tela e foco funcionam como num formulário comum. Status nunca só por cor: a escolhida ganha
 * um visto.
 */
export function Opcoes({
  legenda,
  nome,
  tipo,
  forma = 'pilula',
  opcoes,
  selecionadas,
  aoMudar,
  maximo,
  erro,
  dica,
  legendaOculta = false,
}: OpcoesProps) {
  const id = useId();
  const idDoErro = `${id}-erro`;
  const idDaDica = `${id}-dica`;
  const idDoMaximo = `${id}-maximo`;
  const cheio = tipo === 'varias' && maximo !== undefined && selecionadas.length >= maximo;
  const descricao = [erro ? idDoErro : '', dica ? idDaDica : '', cheio ? idDoMaximo : ''].filter(Boolean).join(' ');

  function trocar(valor: string, marcada: boolean) {
    if (tipo === 'unica') {
      aoMudar([valor]);
    } else if (marcada) {
      aoMudar([...selecionadas, valor]);
    } else {
      aoMudar(selecionadas.filter((escolhida) => escolhida !== valor));
    }
  }

  return (
    <fieldset className={estilos.grupo} aria-describedby={descricao || undefined}>
      <legend className={legendaOculta ? 'sr-only' : estilos.legenda}>{legenda}</legend>
      {dica && (
        <p className={estilos.dica} id={idDaDica}>
          {dica}
        </p>
      )}
      <div className={estilos.opcoes} data-forma={forma}>
        {opcoes.map((opcao) => {
          const marcada = selecionadas.includes(opcao.valor);
          return (
            <label key={opcao.valor} className={estilos.opcao} data-forma={forma}>
              <input
                className={estilos.entrada}
                type={tipo === 'unica' ? 'radio' : 'checkbox'}
                name={nome}
                value={opcao.valor}
                checked={marcada}
                disabled={cheio && !marcada}
                aria-invalid={erro ? true : undefined}
                onChange={(evento) => trocar(opcao.valor, evento.target.checked)}
              />
              {opcao.imagem && <span className={estilos.imagem}>{opcao.imagem}</span>}
              {opcao.curto ? (
                <>
                  <span aria-hidden="true">{opcao.curto}</span>
                  <span className="sr-only">{opcao.texto}</span>
                </>
              ) : (
                <span className={estilos.texto}>
                  <b>{opcao.texto}</b>
                  {opcao.detalhe && <span className={estilos.detalhe}>{opcao.detalhe}</span>}
                </span>
              )}
              {forma !== 'dia' && <Icone nome="check" className={estilos.visto} />}
            </label>
          );
        })}
      </div>
      {cheio && (
        <p className={estilos.dica} id={idDoMaximo}>
          {`Você já escolheu o máximo (${maximo}).`}
        </p>
      )}
      {erro && (
        <p className={estilos.erro} id={idDoErro}>
          {erro}
        </p>
      )}
    </fieldset>
  );
}
