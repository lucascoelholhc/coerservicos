import { useState } from 'react';

import { comoErroDaApi } from '../../api/erros';
import { buscarFotosPendentes, decidirFoto, type FotoPendente, type PendenciaDeFotos } from '../../api/painelAdmin';
import { useRecurso } from '../../api/useRecurso';
import { urlDaFotoParaAdmin } from '../../api/verificacao';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Admin.module.css';
import { ArquivoPorLink } from './VerificacaoDoCadastro';

async function carregar(sinal: AbortSignal): Promise<PendenciaDeFotos[]> {
  return (await buscarFotosPendentes(sinal)).itens;
}

/** Fotos novas de perfis já aprovados (FE-07, RN15a/RN57): ocultas até o admin aprovar. */
export function FotosPendentes() {
  useTitulo('Fotos novas para conferir');
  const recurso = useRecurso(carregar);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <p className={estilos.chamada}>Equipe COE</p>
        <h1>Fotos novas para conferir</h1>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando as fotos…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {(recurso.situacao === 'pronto' || recurso.situacao === 'vazio') && <Lista iniciais={recurso.dados} />}
    </section>
  );
}

function Lista({ iniciais }: { iniciais: PendenciaDeFotos[] }) {
  const [pendencias, setPendencias] = useState(iniciais);
  const [aviso, setAviso] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function decidir(perfil: string, foto: FotoPendente, aprovar: boolean) {
    setErro(null);
    void executar(
      async () => {
        await decidirFoto(perfil, foto.id, aprovar);
        setPendencias((atuais) =>
          atuais
            .map((p) => (p.profissionalId === perfil ? { ...p, fotos: p.fotos.filter((f) => f.id !== foto.id) } : p))
            .filter((p) => p.fotos.length > 0),
        );
        setAviso(aprovar ? 'Foto aprovada. Ela já aparece no perfil.' : 'Foto recusada. Ela continua fora do perfil.');
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  return (
    <>
      {aviso && (
        <Nota tipo="ok" anunciar>
          {aviso}
        </Nota>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {pendencias.length === 0 ? (
        <div className={estilos.vazio}>
          <p>Nenhuma foto esperando conferência.</p>
        </div>
      ) : (
        <ul className={estilos.cartoes}>
          {pendencias.map((pendencia) => (
            <li key={pendencia.profissionalId}>
              <article className={estilos.cartao} aria-labelledby={`pend-${pendencia.profissionalId}`}>
                <h2 id={`pend-${pendencia.profissionalId}`}>{pendencia.nome}</h2>
                <ul className={estilos.lista}>
                  {pendencia.fotos.map((foto) => {
                    const nome = foto.legenda ?? foto.servico ?? 'sem legenda';
                    return (
                      <li key={foto.id}>
                        <p>{`${foto.servico ?? 'Serviço'} · ${nome}`}</p>
                        <ArquivoPorLink
                          rotulo={`a foto ${nome}`}
                          buscar={() => urlDaFotoParaAdmin(pendencia.profissionalId, foto.id)}
                        />
                        <Botao
                          variante="sucesso"
                          tamanho="sm"
                          onClick={() => decidir(pendencia.profissionalId, foto, true)}
                          desabilitado={enviando}
                        >
                          {`Aprovar a foto ${nome}`}
                        </Botao>
                        <Botao
                          variante="perigo"
                          tamanho="sm"
                          onClick={() => decidir(pendencia.profissionalId, foto, false)}
                          desabilitado={enviando}
                        >
                          {`Recusar a foto ${nome}`}
                        </Botao>
                      </li>
                    );
                  })}
                </ul>
              </article>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
