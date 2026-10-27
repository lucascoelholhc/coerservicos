import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { listarMinhasDiarias } from '../../api/areaDoProfissional';
import { enviarFotoDoDia, terminei } from '../../api/diarias';
import type { DiariaDoProfissional, DiariaExecutada } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoArquivo } from '../../componentes/CampoArquivo/CampoArquivo';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { formatarDiaEHora, formatarDiaLongo } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Profissional.module.css';
import { FOTOS_POR_DIA, RECADOS } from './textos';

const TAMANHO_DO_RECADO = 300;

/** Terminei o dia (FE-06, protótipo proTerminar): foto do serviço, recado e envio para o cliente aprovar. */
export function TermineiODia() {
  useTitulo('Terminei o dia');
  const { id = '' } = useParams();
  const recurso = useRecurso(listarMinhasDiarias);
  const diaria = recurso.situacao === 'pronto' ? recurso.dados.itens.find((d) => d.id === id) : undefined;

  return (
    <section className={`largura ${estilos.pagina}`}>
      <Link className={estilos.voltar} to={CAMINHOS.profissionalHoje}>
        <Icone nome="back" tamanho={18} />
        Voltar
      </Link>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && !diaria && (
        <div className={estilos.cartao}>
          <h1>Diária não encontrada</h1>
          <Botao para={CAMINHOS.profissionalHoje}>Voltar para Hoje</Botao>
        </div>
      )}
      {diaria && <Formulario diaria={diaria} />}
    </section>
  );
}

function Formulario({ diaria }: { diaria: DiariaDoProfissional }) {
  const [fotos, setFotos] = useState<string[]>([]);
  const [frases, setFrases] = useState<string[]>([]);
  const [texto, setTexto] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviado, setEnviado] = useState<DiariaExecutada | null>(null);
  const { enviando, executar } = useEnvioUnico();

  // as prévias são do aparelho (blob:); saem da memória quando a tela fecha
  useEffect(() => () => fotos.forEach((url) => URL.revokeObjectURL(url)), [fotos]);

  if (enviado || diaria.status === 'aguardando') {
    return <DiaEnviado diaria={diaria} autoLiberaEm={enviado?.autoLiberaEm ?? diaria.autoLiberaEm} />;
  }
  if (diaria.status !== 'andamento') {
    return (
      <div className={estilos.cartao}>
        <h1>Terminei o dia</h1>
        <Nota tipo="atencao">Marque "Cheguei na obra" no dia do trabalho antes de terminar o dia.</Nota>
        <Botao para={CAMINHOS.profissionalHoje}>Voltar para Hoje</Botao>
      </div>
    );
  }

  const recado = [...frases, texto.trim()].filter((parte) => parte !== '').join('. ');

  function escolher(arquivos: File[]) {
    setErro(null);
    const vagas = FOTOS_POR_DIA - fotos.length;
    const escolhidas = arquivos.slice(0, vagas);
    void executar(
      async () => {
        for (const arquivo of escolhidas) {
          await enviarFotoDoDia(diaria.id, arquivo);
          setFotos((antes) => [...antes, URL.createObjectURL(arquivo)]);
        }
        if (arquivos.length > vagas) {
          setErro(`Cada dia aceita até ${FOTOS_POR_DIA} fotos.`);
        }
      },
      (falha) => setErro(falha.message),
    );
  }

  function alternar(frase: string) {
    setFrases((antes) => (antes.includes(frase) ? antes.filter((f) => f !== frase) : [...antes, frase]));
  }

  function enviar() {
    setErro(null);
    void executar(
      async () => setEnviado(await terminei(diaria.id, recado)),
      (falha) => setErro(falha.message),
    );
  }

  return (
    <>
      <header className={estilos.topo}>
        <h1>Terminei o dia</h1>
        <p className={estilos.apoio}>
          {formatarDiaLongo(diaria.data)} · obra de {diaria.cliente}
        </p>
      </header>

      <section className={`${estilos.cartao} ${estilos.passo}`} aria-labelledby="passo-foto">
        <h2 id="passo-foto">
          <span className={estilos.numeroDoPasso} aria-hidden="true">
            1
          </span>
          Tire uma foto do serviço
        </h2>
        <CampoArquivo
          rotulo="Foto do que você fez hoje"
          botao={fotos.length === 0 ? 'Tirar foto' : 'Tirar mais uma foto'}
          captura="environment"
          varios
          grande
          desabilitado={enviando || fotos.length >= FOTOS_POR_DIA}
          aoEscolher={escolher}
        >
          {fotos.length > 0 && (
            <>
              <p role="status">
                {fotos.length} {fotos.length === 1 ? 'foto enviada' : 'fotos enviadas'}.
              </p>
              <ul className={estilos.miniaturas}>
                {fotos.map((url, posicao) => (
                  <li key={url}>
                    <img src={url} alt={`Foto ${posicao + 1} do serviço`} />
                  </li>
                ))}
              </ul>
            </>
          )}
        </CampoArquivo>
      </section>

      <section className={`${estilos.cartao} ${estilos.passo}`} aria-labelledby="passo-recado">
        <h2 id="passo-recado">
          <span className={estilos.numeroDoPasso} aria-hidden="true">
            2
          </span>
          Algum recado? <span className={estilos.apoio}>(se quiser)</span>
        </h2>
        <div className={estilos.recados} role="group" aria-label="Recados prontos">
          {RECADOS.map((frase) => (
            <button
              key={frase}
              type="button"
              className={estilos.recado}
              aria-pressed={frases.includes(frase)}
              onClick={() => alternar(frase)}
            >
              {frase}
            </button>
          ))}
        </div>
        <CampoTexto
          nome="recado"
          rotulo="Ou escreva"
          valor={texto}
          aoMudar={setTexto}
          maxLength={TAMANHO_DO_RECADO}
          dica="Sem telefone nem rede social: o cliente já tem seu contato."
        />
      </section>

      <section className={estilos.passo} aria-labelledby="passo-enviar">
        <h2 id="passo-enviar">
          <span className={estilos.numeroDoPasso} aria-hidden="true">
            3
          </span>
          Enviar para o cliente
        </h2>
        <Botao tamanho="xl" onClick={enviar} desabilitado={enviando || fotos.length === 0}>
          <Icone nome="send" tamanho={24} />
          Enviar e terminar o dia
        </Botao>
        <p className={estilos.apoio}>
          {fotos.length === 0
            ? 'Mande pelo menos uma foto para terminar o dia.'
            : 'O cliente aprova o dia. Se não responder no prazo, o dinheiro é liberado para você.'}
        </p>
      </section>
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
    </>
  );
}

function DiaEnviado({ diaria, autoLiberaEm }: { diaria: DiariaDoProfissional; autoLiberaEm: string | null }) {
  return (
    <div className={estilos.cartao}>
      <h1>Dia enviado</h1>
      <Nota tipo="ok" anunciar>
        {diaria.cliente} recebeu a foto
        {autoLiberaEm ? ` e tem até ${formatarDiaEHora(autoLiberaEm)} para aprovar` : ''}. Se não responder, os{' '}
        {formatarReais(diaria.repasse)} são liberados para você do mesmo jeito.
      </Nota>
      <Botao tamanho="xl" para={CAMINHOS.profissionalHoje}>
        Voltar para Hoje
      </Botao>
    </div>
  );
}
