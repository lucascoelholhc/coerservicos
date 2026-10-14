import { useEffect, useState } from 'react';

import { comoErroDaApi } from '../../api/erros';
import { Botao } from '../Botao/Botao';
import { Nota } from '../Nota/Nota';
import estilos from './CampoCodigo.module.css';

/** O servidor recusa um segundo envio para o mesmo destino antes de 60 s; a contagem evita o pedido recusado. */
export const ESPERA_PADRAO_EM_SEGUNDOS = 60;

const TEXTOS = {
  codigo: { rotulo: 'Mandar outro código', espera: 'Pode pedir outro código em', feito: 'Mandamos outro código.' },
  link: { rotulo: 'Mandar o link de novo', espera: 'Pode pedir outro link em', feito: 'Mandamos o link de novo.' },
} as const;

interface MandarDeNovoProps {
  aoReenviar: () => Promise<void>;
  item?: keyof typeof TEXTOS;
  /** Segundos até poder pedir de novo; a contagem começa ao abrir (algo acabou de ser enviado). */
  espera?: number;
}

/** Reenviar código ou link: desabilitado durante a espera, com a contagem visível; uma falha (ex.: 429) vira aviso. */
export function MandarDeNovo({ aoReenviar, item = 'codigo', espera = ESPERA_PADRAO_EM_SEGUNDOS }: MandarDeNovoProps) {
  const textos = TEXTOS[item];
  const [restante, setRestante] = useState(espera);
  const [enviando, setEnviando] = useState(false);
  const [retorno, setRetorno] = useState<{ tipo: 'ok' | 'erro'; texto: string } | null>(null);
  const contando = restante > 0;

  useEffect(() => {
    if (!contando) {
      return undefined;
    }
    const relogio = window.setInterval(() => setRestante((atual) => Math.max(0, atual - 1)), 1000);
    return () => window.clearInterval(relogio);
  }, [contando]);

  async function mandar() {
    setEnviando(true);
    setRetorno(null);
    try {
      await aoReenviar();
      setRetorno({ tipo: 'ok', texto: textos.feito });
      setRestante(espera);
    } catch (falha) {
      setRetorno({ tipo: 'erro', texto: comoErroDaApi(falha).message });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className={estilos.reenviar}>
      <Botao variante="suave" tamanho="sm" onClick={() => void mandar()} desabilitado={contando || enviando}>
        {enviando ? 'Mandando…' : textos.rotulo}
      </Botao>
      {contando && <p className={estilos.espera}>{`${textos.espera} ${restante} s.`}</p>}
      {retorno && (
        <Nota tipo={retorno.tipo} anunciar>
          {retorno.texto}
        </Nota>
      )}
    </div>
  );
}
