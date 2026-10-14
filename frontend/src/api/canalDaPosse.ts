/*
 * RN61, prova de posse do e-mail: o link do e-mail abre a /provar-email (em geral numa aba nova) e o
 * comprovante volta para a aba do cadastro pelo BroadcastChannel (mesma origem, mesmo navegador).
 * Vai só {tipo, email, comprovante}; a senha nunca passa entre abas. A aba do cadastro aceita só o
 * e-mail (normalizado) que ela está provando, responde, para de ouvir e fecha o canal. A /provar-email
 * espera a resposta por pouco tempo; sem resposta (aba fechada ou navegador sem BroadcastChannel),
 * ela mesma continua o cadastro.
 */

const NOME_DO_CANAL = 'coe-posse';
const TIPO_DO_COMPROVANTE = 'coe-posse-email';
const TIPO_DA_RESPOSTA = 'coe-posse-recebido';
/** Quanto a /provar-email espera a resposta da aba do cadastro. */
const ESPERA_DA_RESPOSTA_MS = 1000;

/** Igual ao backend (Contato.normalizarEmail): sem espaços nas pontas e em minúsculas. */
function normalizarEmail(email: string): string {
  return email.trim().toLowerCase();
}

function temCanal(): boolean {
  return typeof BroadcastChannel !== 'undefined';
}

function campos(dado: unknown): Record<string, unknown> {
  return typeof dado === 'object' && dado !== null ? (dado as Record<string, unknown>) : {};
}

/** O comprovante, se a mensagem for do tipo certo e do e-mail esperado; qualquer outra coisa = null. */
function comprovanteDe(dado: unknown, email: string): string | null {
  const { tipo, email: doDado, comprovante } = campos(dado);
  if (tipo !== TIPO_DO_COMPROVANTE || typeof doDado !== 'string' || typeof comprovante !== 'string' || !comprovante) {
    return null;
  }
  return normalizarEmail(doDado) === email ? comprovante : null;
}

/** Aba do cadastro: ouve o comprovante do e-mail que está provando. Devolve a função de parar. */
export function ouvirComprovanteDoEmail(email: string, aoReceber: (comprovante: string) => void): () => void {
  if (!temCanal()) {
    return () => undefined;
  }
  const esperado = normalizarEmail(email);
  const canal = new BroadcastChannel(NOME_DO_CANAL);
  let aberto = true;
  function parar() {
    if (aberto) {
      aberto = false;
      canal.close();
    }
  }
  canal.onmessage = (evento: MessageEvent) => {
    const comprovante = comprovanteDe(evento.data, esperado);
    if (comprovante === null) {
      return;
    }
    canal.postMessage({ tipo: TIPO_DA_RESPOSTA, email: esperado });
    parar();
    aoReceber(comprovante);
  };
  return parar;
}

/** /provar-email: publica o comprovante e diz se a aba do cadastro desse e-mail respondeu a tempo. */
export function entregarComprovanteDoEmail(email: string, comprovante: string): Promise<boolean> {
  if (!temCanal()) {
    return Promise.resolve(false);
  }
  const esperado = normalizarEmail(email);
  const canal = new BroadcastChannel(NOME_DO_CANAL);
  return new Promise((resolver) => {
    const relogio = setTimeout(() => terminar(false), ESPERA_DA_RESPOSTA_MS);
    function terminar(entregue: boolean) {
      clearTimeout(relogio);
      canal.close();
      resolver(entregue);
    }
    canal.onmessage = (evento: MessageEvent) => {
      const { tipo, email: doDado } = campos(evento.data);
      if (tipo === TIPO_DA_RESPOSTA && doDado === esperado) {
        terminar(true);
      }
    };
    canal.postMessage({ tipo: TIPO_DO_COMPROVANTE, email: esperado, comprovante });
  });
}
