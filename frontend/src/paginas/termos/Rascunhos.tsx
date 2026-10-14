import type { ReactNode } from 'react';

import { useTitulo } from '../../layout/titulo';
import estilos from './Rascunhos.module.css';

/*
 * Termos de uso e política de privacidade em rascunho. O texto final (LGPD, RNF18) é pendência
 * obrigatória antes do lançamento; quem redige: A DEFINIR (registrado no plano).
 */
function PaginaDeRascunho({ titulo, children }: { titulo: string; children: ReactNode }) {
  useTitulo(titulo);
  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <p className={estilos.etiqueta}>Rascunho</p>
        <h1>{titulo}</h1>
        <p className={estilos.aviso}>O texto final será publicado antes do lançamento da COE.</p>
        {children}
      </div>
    </section>
  );
}

export function Termos() {
  return (
    <PaginaDeRascunho titulo="Termos de uso">
      <p>
        Aqui ficarão as regras de uso da COE: como funciona o pagamento antecipado das diárias, a liberação dia a dia,
        as reclamações e a troca de contato só depois de contratar.
      </p>
    </PaginaDeRascunho>
  );
}

export function Privacidade() {
  return (
    <PaginaDeRascunho titulo="Política de privacidade">
      <p>
        Aqui ficará como a COE trata os seus dados pessoais, conforme a Lei Geral de Proteção de Dados (LGPD): o que é
        guardado, por quanto tempo e como pedir a exclusão.
      </p>
    </PaginaDeRascunho>
  );
}
