import { useState } from 'react';

import { apagarFoto, buscarCadastro, descreverFoto, enviarFoto, urlDaFoto } from '../../../api/cadastroProfissional';
import { comoErroDaApi, ErroDaApi } from '../../../api/erros';
import type { CadastroDoProfissional, FotoDoCadastro } from '../../../api/tipos';
import { CampoArquivo } from '../../../componentes/CampoArquivo/CampoArquivo';
import { CampoSelecao } from '../../../componentes/CampoSelecao/CampoSelecao';
import { CampoTexto } from '../../../componentes/CampoTexto/CampoTexto';
import { Icone } from '../../../icones/Icone';
import estilos from '../Cadastro.module.css';
import { QuadroDaEtapa } from '../QuadroDaEtapa';
import { usePrevias, useSalvarEtapa, type PropsDaEtapa } from './comum';

interface Descricao {
  servico: string;
  legenda: string;
}

function descricaoDe(foto: FotoDoCadastro): Descricao {
  return { servico: foto.servico ?? '', legenda: foto.legenda ?? '' };
}

async function rascunhoAtual(): Promise<CadastroDoProfissional> {
  const atualizado = await buscarCadastro(new AbortController().signal);
  if (atualizado === null) {
    throw new ErroDaApi('resposta');
  }
  return atualizado;
}

/** Etapa 9: fotos de trabalhos já feitos, cada uma com o serviço e uma legenda (se quiser). */
export function EtapaFotos({ numero, cadastro, aoSalvar, aoAtualizar, aoVoltar }: PropsDaEtapa) {
  const [descricoes, setDescricoes] = useState<Record<string, Descricao>>(() =>
    Object.fromEntries(cadastro.fotos.map((foto) => [foto.id, descricaoDe(foto)])),
  );
  const [mandando, setMandando] = useState(false);
  const [erroDoEnvio, setErroDoEnvio] = useState<string | undefined>();
  const [enderecos, setEnderecos] = useState<Record<string, string>>({});
  const { previas, guardar } = usePrevias();
  const { enviando, erro, salvar, avisar } = useSalvarEtapa(aoSalvar);
  const servicos = [...new Set(cadastro.servicos.map((s) => s.nome))];

  function atualizar(atualizado: CadastroDoProfissional) {
    setDescricoes((anteriores) => {
      const novas = { ...anteriores };
      for (const foto of atualizado.fotos) {
        novas[foto.id] ??= descricaoDe(foto);
      }
      return novas;
    });
    aoAtualizar(atualizado);
  }

  async function mandar(arquivos: File[]) {
    setMandando(true);
    setErroDoEnvio(undefined);
    try {
      for (const arquivo of arquivos) {
        const foto = await enviarFoto(arquivo, {});
        guardar(foto.id, arquivo);
      }
    } catch (falha) {
      setErroDoEnvio(comoErroDaApi(falha).message);
    }
    try {
      atualizar(await rascunhoAtual());
    } catch (falha) {
      setErroDoEnvio(comoErroDaApi(falha).message);
    } finally {
      setMandando(false);
    }
  }

  async function tirar(id: string) {
    setMandando(true);
    setErroDoEnvio(undefined);
    try {
      await apagarFoto(id);
      atualizar(await rascunhoAtual());
    } catch (falha) {
      setErroDoEnvio(comoErroDaApi(falha).message);
    } finally {
      setMandando(false);
    }
  }

  async function ver(id: string) {
    try {
      const { url } = await urlDaFoto(id);
      // Só endereço http(s): o link nunca vira javascript: ou outro esquema, mesmo com resposta estranha.
      if (!/^https?:\/\//i.test(url)) {
        throw new ErroDaApi('resposta');
      }
      setEnderecos((anteriores) => ({ ...anteriores, [id]: url }));
    } catch (falha) {
      setErroDoEnvio(comoErroDaApi(falha).message);
    }
  }

  function enviar() {
    if (cadastro.fotos.length === 0) {
      avisar({}, 'Mande pelo menos 1 foto para continuar.');
      return;
    }
    salvar(async () => {
      for (const [indice, foto] of cadastro.fotos.entries()) {
        const descricao = descricoes[foto.id] ?? descricaoDe(foto);
        if (descricao.servico === (foto.servico ?? '') && descricao.legenda === (foto.legenda ?? '')) {
          continue;
        }
        try {
          await descreverFoto(foto.id, {
            servico: descricao.servico === '' ? null : descricao.servico,
            legenda: descricao.legenda.trim() === '' ? null : descricao.legenda.trim(),
          });
        } catch (falha) {
          const problema = comoErroDaApi(falha);
          throw new ErroDaApi(problema.tipo, {
            status: problema.status,
            codigo: problema.codigo,
            mensagem: `Foto ${indice + 1}: ${problema.message}`,
          });
        }
      }
      return rascunhoAtual();
    });
  }

  function mudar(id: string, campo: keyof Descricao, valor: string) {
    setDescricoes((anteriores) => ({
      ...anteriores,
      [id]: { ...(anteriores[id] ?? { servico: '', legenda: '' }), [campo]: valor },
    }));
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando || mandando} erro={erro}>
      <p className={estilos.apagado}>Quem mostra trabalhos é mais contratado. Mande pelo menos 1 foto. O ideal são 4 ou mais.</p>
      <CampoArquivo
        rotulo="Fotos de trabalhos que você já fez"
        botao={mandando ? 'Mandando…' : 'Adicionar fotos'}
        varios
        grande
        desabilitado={mandando}
        erro={erroDoEnvio}
        aoEscolher={(arquivos) => void mandar(arquivos)}
      />
      {cadastro.fotos.length > 0 && (
        <ul className={estilos.fotos}>
          {cadastro.fotos.map((foto, indice) => {
            const descricao = descricoes[foto.id] ?? descricaoDe(foto);
            const previa = previas[foto.id];
            return (
              <li key={foto.id} className={estilos.foto}>
                {previa ? (
                  <img className={estilos.imagemDaFoto} src={previa} alt={`Foto ${indice + 1}`} />
                ) : (
                  <div className={estilos.semPrevia}>
                    <span>{`Foto ${indice + 1}`}</span>
                    {enderecos[foto.id] ? (
                      <a href={enderecos[foto.id]} target="_blank" rel="noreferrer">
                        {`Abrir a foto ${indice + 1}`}
                      </a>
                    ) : (
                      <button type="button" className={estilos.linkBotao} onClick={() => void ver(foto.id)}>
                        {`Ver a foto ${indice + 1}`}
                      </button>
                    )}
                  </div>
                )}
                <CampoSelecao
                  nome={`servico-${foto.id}`}
                  rotulo={`Que serviço é a foto ${indice + 1}?`}
                  opcoes={[{ valor: '', texto: 'Escolha…' }, ...servicos.map((nome) => ({ valor: nome, texto: nome }))]}
                  valor={descricao.servico}
                  aoMudar={(valor) => mudar(foto.id, 'servico', valor)}
                />
                <CampoTexto
                  nome={`legenda-${foto.id}`}
                  rotulo={`Legenda da foto ${indice + 1} (se quiser)`}
                  valor={descricao.legenda}
                  aoMudar={(valor) => mudar(foto.id, 'legenda', valor)}
                  maxLength={140}
                  dica="Ex.: muro de 12 m"
                />
                <button type="button" className={estilos.tirar} onClick={() => void tirar(foto.id)} disabled={mandando}>
                  <Icone nome="trash" />
                  {`Tirar a foto ${indice + 1}`}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </QuadroDaEtapa>
  );
}
