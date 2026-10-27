import { useCallback, useState } from 'react';

import { aprovarDia, fotosDoDia } from '../../api/diarias';
import type { DiaDoContrato as Dia } from '../../api/tipos';
import { enderecoSeguro } from '../../api/urlAssinada';
import { useRecurso } from '../../api/useRecurso';
import { Botao } from '../../componentes/Botao/Botao';
import { Carimbo } from '../../componentes/Carimbo/Carimbo';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { StatusDiaria } from '../../componentes/StatusDiaria/StatusDiaria';
import { formatarDiaEHora, formatarDiaLongo } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Contratos.module.css';

interface Props {
  dia: Dia;
  /** O primeiro dia esperando aprovação leva o botão principal (um por tela). */
  principal: boolean;
  aoMudar: () => Promise<void>;
}

/** Uma diária no contrato do cliente (FE-05, dia 35): fotos do "Terminei", aprovar e o prazo da liberação. */
export function DiaDoContrato({ dia, principal, aoMudar }: Props) {
  const [acabouDeAprovar, setAcabouDeAprovar] = useState(false);
  return (
    <li className={estilos.dia}>
      <div className={estilos.diaTopo}>
        <span>
          <b>{formatarDiaLongo(dia.data)}</b>
          <span className={estilos.apoio}>
            {dia.tipo === 'meia' ? 'Meia diária' : 'Diária inteira'} · {formatarReais(dia.valor)}
          </span>
        </span>
        {dia.status === 'liberada' ? (
          <Carimbo tipo="aprovado" compacto animar={acabouDeAprovar} />
        ) : (
          <StatusDiaria estado={dia.status} lado="cliente" />
        )}
      </div>
      {dia.status === 'aguardando' && (
        <Aprovar
          dia={dia}
          principal={principal}
          aoMudar={async () => {
            setAcabouDeAprovar(true);
            await aoMudar();
          }}
        />
      )}
      {acabouDeAprovar && dia.status === 'liberada' && (
        <Nota tipo="ok" anunciar>
          Dia aprovado. O dinheiro deste dia foi liberado para o profissional.
        </Nota>
      )}
    </li>
  );
}

function Aprovar({ dia, principal, aoMudar }: Props) {
  const buscar = useCallback((sinal: AbortSignal) => fotosDoDia(dia.id, sinal), [dia.id]);
  const fotos = useRecurso(buscar, undefined, dia.id);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function aprovar() {
    setErro(null);
    void executar(
      async () => {
        await aprovarDia(dia.id);
        await aoMudar();
      },
      (falha) => setErro(falha.message),
    );
  }

  return (
    <div className={estilos.aprovar}>
      {fotos.situacao === 'carregando' && <p role="status">Carregando as fotos…</p>}
      {fotos.situacao === 'erro' && <FalhaAoCarregar erro={fotos.erro} aoTentarDeNovo={fotos.tentarDeNovo} />}
      {fotos.situacao === 'pronto' && (
        <>
          <ul className={estilos.fotosDoDia} aria-label="Fotos do serviço">
            {fotos.dados.fotos
              .filter((foto) => enderecoSeguro(foto.url))
              .map((foto, posicao) => (
                <li key={foto.id}>
                  <img src={foto.url} alt={`Foto ${posicao + 1} do serviço do dia`} loading="lazy" />
                </li>
              ))}
          </ul>
          {fotos.dados.recado && <p>Recado: “{fotos.dados.recado}”</p>}
        </>
      )}
      <Botao variante={principal ? 'principal' : 'contorno'} onClick={aprovar} desabilitado={enviando}>
        Aprovar o dia
      </Botao>
      {dia.autoLiberaEm && (
        <p className={estilos.apoio}>
          Se você não responder até {formatarDiaEHora(dia.autoLiberaEm)}, o dia é liberado sozinho para o profissional.
        </p>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
    </div>
  );
}
