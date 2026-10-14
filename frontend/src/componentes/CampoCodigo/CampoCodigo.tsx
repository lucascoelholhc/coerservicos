import { useEffect, useState, type Ref } from 'react';

import { ErroDaApi } from '../../api/erros';
import { Botao } from '../Botao/Botao';
import { CampoTexto } from '../CampoTexto/CampoTexto';
import { Nota } from '../Nota/Nota';
import estilos from './CampoCodigo.module.css';

export const DIGITOS_DO_CODIGO = 6;

/** O servidor recusa um segundo envio para o mesmo destino antes de 60 s; a contagem evita o pedido recusado. */
const ESPERA_PADRAO_EM_SEGUNDOS = 60;

export function codigoCompleto(codigo: string): boolean {
  return codigo.length === DIGITOS_DO_CODIGO;
}

interface CampoCodigoProps {
  valor: string;
  aoMudar: (codigo: string) => void;
  rotulo?: string;
  erro?: string;
  ref?: Ref<HTMLInputElement>;
  /** Pede outro código. Sem ela, não há "Mandar outro código" (ex.: segundo passo do MFA). */
  aoReenviar?: () => Promise<void>;
  /** Segundos até poder pedir outro código; a contagem começa ao abrir (um código acabou de ser enviado). */
  espera?: number;
}

/** O código de 6 números que chega por SMS, usado em toda tela de código. */
export function CampoCodigo({
  valor,
  aoMudar,
  rotulo = 'Código',
  erro,
  ref,
  aoReenviar,
  espera = ESPERA_PADRAO_EM_SEGUNDOS,
}: CampoCodigoProps) {
  return (
    <div className={estilos.campo}>
      <CampoTexto
        nome="codigo"
        rotulo={rotulo}
        valor={valor}
        aoMudar={(texto) => aoMudar(texto.replace(/\D/g, '').slice(0, DIGITOS_DO_CODIGO))}
        inputMode="numeric"
        autoComplete="one-time-code"
        erro={erro}
        ref={ref}
      />
      {aoReenviar && <MandarOutro aoReenviar={aoReenviar} espera={espera} />}
    </div>
  );
}

function MandarOutro({ aoReenviar, espera }: { aoReenviar: () => Promise<void>; espera: number }) {
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
      setRetorno({ tipo: 'ok', texto: 'Mandamos outro código.' });
      setRestante(espera);
    } catch (falha) {
      setRetorno({ tipo: 'erro', texto: (falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor')).message });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className={estilos.reenviar}>
      <Botao variante="suave" tamanho="sm" onClick={() => void mandar()} desabilitado={contando || enviando}>
        {enviando ? 'Mandando…' : 'Mandar outro código'}
      </Botao>
      {contando && <p className={estilos.espera}>{`Pode pedir outro código em ${restante} s.`}</p>}
      {retorno && (
        <Nota tipo={retorno.tipo} anunciar>
          {retorno.texto}
        </Nota>
      )}
    </div>
  );
}
