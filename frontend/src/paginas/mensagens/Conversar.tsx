import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { abrirConversa } from '../../api/conversas';
import { comoErroDaApi } from '../../api/erros';
import { CAMINHOS, caminhoDaConversa } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import estilos from './Mensagens.module.css';

/** "Tirar dúvida pelo chat": abre (ou reabre) a conversa com o profissional e vai para ela. */
export function Conversar() {
  const { perfil = '' } = useParams();
  const navegar = useNavigate();
  const [erro, setErro] = useState<string | null>(null);
  const aberta = useRef<string | null>(null);
  useTitulo('Conversa');

  useEffect(() => {
    // uma vez por perfil (o StrictMode roda o efeito duas vezes; outro perfil abre outra conversa)
    if (aberta.current === perfil) {
      return;
    }
    aberta.current = perfil;
    abrirConversa(perfil).then(
      (conversa) => void navegar(caminhoDaConversa(conversa.id), { replace: true }),
      (falha: unknown) => setErro(comoErroDaApi(falha).message),
    );
  }, [perfil, navegar]);

  return (
    <section className={`largura ${estilos.pagina}`}>
      {erro ? (
        <>
          <Nota tipo="erro">{erro}</Nota>
          <Botao para={CAMINHOS.mensagens}>Ver minhas mensagens</Botao>
        </>
      ) : (
        <p role="status">Abrindo a conversa…</p>
      )}
    </section>
  );
}
