import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { buscarMensagens, enviarMensagem, listarConversas, marcarLidas } from '../../api/conversas';
import type { EnvioDaMensagem, MensagemDoChat, ResumoDaConversa } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoTextoLongo } from '../../componentes/CampoTextoLongo/CampoTextoLongo';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { formatarDiaEHora } from '../../formatacao/datas';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Mensagens.module.css';

/** As regras da censura em palavras simples (RN47). */
const MOTIVOS: Record<string, string> = {
  telefone: 'telefone',
  email: 'e-mail',
  link: 'link',
  perfil: '@perfil de rede social',
  rede: 'nome de rede social',
  pedido: 'pedido de contato fora do app',
};

interface Dados {
  conversa: ResumoDaConversa | undefined;
  mensagens: MensagemDoChat[];
}

async function carregar(id: string, sinal: AbortSignal): Promise<Dados> {
  const [lista, mensagens] = await Promise.all([listarConversas(sinal), buscarMensagens(id, sinal)]);
  return { conversa: lista.itens.find((c) => c.id === id), mensagens: mensagens.itens };
}

/** Uma conversa do chat (FE-05, protótipo V.conversa e chatHTML). */
export function Conversa() {
  const { id = '' } = useParams();
  const buscar = useCallback((sinal: AbortSignal) => carregar(id, sinal), [id]);
  const recurso = useRecurso(buscar, undefined, id);
  useTitulo('Conversa');

  // abriu a conversa: as mensagens do outro lado ficam lidas (falha aqui não atrapalha a leitura)
  useEffect(() => {
    void marcarLidas(id).catch(() => undefined);
  }, [id]);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <Link className={estilos.voltar} to={CAMINHOS.mensagens}>
        <Icone nome="back" tamanho={18} />
        Mensagens
      </Link>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Chat id={id} dados={recurso.dados} />}
    </section>
  );
}

function Chat({ id, dados }: { id: string; dados: Dados }) {
  const [mensagens, setMensagens] = useState(dados.mensagens);
  const [texto, setTexto] = useState('');
  const [aviso, setAviso] = useState<EnvioDaMensagem['aviso']>(null);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();
  const liberado = dados.conversa?.contatoLiberado ?? false;

  function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    if (texto.trim() === '') {
      return;
    }
    setErro(null);
    void executar(
      async () => {
        const envio = await enviarMensagem(id, texto.trim());
        setMensagens((antes) => [...antes, envio.mensagem]);
        setAviso(envio.aviso);
        setTexto('');
      },
      (falha) => setErro(falha.message),
    );
  }

  return (
    <div className={estilos.chat}>
      <header className={estilos.topo}>
        <h1>{dados.conversa?.outroNome ?? 'Conversa'}</h1>
        <p className={estilos.apoio}>
          {liberado
            ? 'Contato liberado: o pagamento foi confirmado.'
            : 'Telefone, e-mail e redes só depois do pagamento. Antes disso, mensagens com contato não são entregues.'}
        </p>
      </header>
      <ol className={estilos.mensagens} aria-label="Mensagens da conversa">
        {mensagens.length === 0 && <li className={estilos.apoio}>Nenhuma mensagem ainda. Diga o que precisa.</li>}
        {mensagens.map((mensagem) => (
          <li key={mensagem.id} className={estilos.mensagem} data-minha={mensagem.minha || undefined}>
            <span className="sr-only">{mensagem.minha ? 'Você:' : `${dados.conversa?.outroNome ?? 'Outro'}:`}</span>
            <p>{mensagem.texto}</p>
            <span className={estilos.hora}>{formatarDiaEHora(mensagem.criadoEm)}</span>
            {mensagem.bloqueada && (
              <span className={estilos.bloqueada}>
                Não foi entregue: tem {mensagem.motivos.map((m) => MOTIVOS[m] ?? m).join(', ')}.
              </span>
            )}
          </li>
        ))}
      </ol>
      {aviso && (
        <Nota tipo="atencao" anunciar>
          {aviso.contaEmAnalise
            ? 'Sua conta foi para análise da equipe COE por tentar passar contato antes do pagamento.'
            : `Tentativa ${aviso.tentativa} de ${aviso.limite}. Passar contato antes do pagamento leva a conta para análise.`}
        </Nota>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      <form className={estilos.envio} onSubmit={enviar}>
        <CampoTextoLongo nome="mensagem" rotulo="Sua mensagem" valor={texto} aoMudar={setTexto} maxLength={2000} />
        <Botao type="submit" desabilitado={enviando}>
          <Icone nome="send" tamanho={18} />
          Enviar
        </Botao>
      </form>
    </div>
  );
}
