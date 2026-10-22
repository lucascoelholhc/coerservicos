import { useContext, useEffect, useRef, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';

import type { CadastroDoProfissional } from '../../api/tipos';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { Nota } from '../../componentes/Nota/Nota';
import { Icone } from '../../icones/Icone';
import estilos from './Cadastro.module.css';
import { ETAPAS, TOTAL_DE_ETAPAS } from './conteudo';
import { TrocouDeEtapa } from './etapas/comum';

interface QuadroDaEtapaProps {
  /** 1 a 11. */
  numero: number;
  cadastro: CadastroDoProfissional;
  aoEnviar: () => void;
  aoVoltar: () => void;
  enviando: boolean;
  /** Erro geral da etapa (da API ou do aviso antes de enviar). */
  erro?: string | null;
  /** Texto do botão principal (padrão "Continuar"). */
  acao?: string;
  children: ReactNode;
}

/**
 * Moldura de cada etapa (protótipo: V.cadastro): "Passo N de 11", barra de progresso com as etapas já
 * completas na API, título que recebe o foco, o formulário e Voltar/Continuar. Uma pergunta por tela.
 */
export function QuadroDaEtapa({ numero, cadastro, aoEnviar, aoVoltar, enviando, erro, acao = 'Continuar', children }: QuadroDaEtapaProps) {
  const descricao = ETAPAS[numero - 1] ?? { chave: 'trabalho', titulo: '', grupo: '' };
  const titulo = useRef<HTMLHeadingElement>(null);
  const trocou = useContext(TrocouDeEtapa);

  // Troca de etapa é troca de tela: o foco vai para o título (a primeira tela fica com o foco do <main>).
  useEffect(() => {
    if (trocou) {
      titulo.current?.focus();
      window.scrollTo?.({ top: 0 });
    }
  }, [trocou, numero]);

  function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    aoEnviar();
  }

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.assistente}>
        <header className={estilos.topo}>
          <p className={estilos.chamada}>{`Passo ${numero} de ${TOTAL_DE_ETAPAS} · ${descricao.grupo}`}</p>
          <div
            className={estilos.progresso}
            role="progressbar"
            aria-label="Progresso do cadastro"
            aria-valuemin={1}
            aria-valuemax={TOTAL_DE_ETAPAS}
            aria-valuenow={numero}
            aria-valuetext={`Passo ${numero} de ${TOTAL_DE_ETAPAS}`}
          >
            {ETAPAS.map((etapa, indice) => (
              <span
                key={etapa.chave}
                data-estado={indice + 1 === numero ? 'atual' : cadastro.etapas[etapa.chave] ? 'feito' : 'falta'}
              />
            ))}
          </div>
          <h1 ref={titulo} tabIndex={-1}>
            {descricao.titulo}
          </h1>
        </header>
        {cadastro.status === 'correcao_pedida' && cadastro.motivoCorrecao && (
          <Nota tipo="atencao">
            <b>A equipe da COE pediu uma correção:</b> {cadastro.motivoCorrecao} Depois de corrigir, envie de novo no
            último passo.
          </Nota>
        )}
        <form className={estilos.formulario} onSubmit={enviar} noValidate>
          {children}
          {erro && (
            <Nota tipo="erro" anunciar>
              {erro}
            </Nota>
          )}
          <div className={estilos.rodape}>
            {numero > 1 && (
              <button type="button" className={estilos.voltar} onClick={aoVoltar} aria-label="Voltar">
                <Icone nome="back" />
              </button>
            )}
            <Botao type="submit" tamanho="lg" desabilitado={enviando}>
              {enviando ? 'Salvando…' : acao}
              {!enviando && numero < TOTAL_DE_ETAPAS && <Icone nome="next" />}
            </Botao>
          </div>
        </form>
        <p className={estilos.depois}>
          <Link to={CAMINHOS.conta}>Continuar depois</Link>
          <span>O que você salvou com o botão de baixo fica guardado.</span>
        </p>
      </div>
    </section>
  );
}
