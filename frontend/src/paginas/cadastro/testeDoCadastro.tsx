import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import type { CadastroDoProfissional, MinhaConta } from '../../api/tipos';
import { CATALOGO_PADRAO, CONTA_PADRAO, REGRAS_PADRAO } from '../../teste/dadosDaApi';
import { CADASTRO_VAZIO } from '../../teste/dadosDoCadastro';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { CadastroProfissional } from './CadastroProfissional';

function OndeEstou() {
  const local = useLocation();
  return <p>{`Fui para ${local.pathname}`}</p>;
}

/** Abre /cadastro (com ?etapa=N, se dado) dentro de um roteador de teste. */
export function abrirCadastro(etapa?: number | string) {
  return render(
    <MemoryRouter initialEntries={[etapa === undefined ? '/cadastro' : `/cadastro?etapa=${etapa}`]}>
      <Routes>
        <Route path="/cadastro" element={<CadastroProfissional />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

type Resposta = Response | Promise<Response>;

interface OpcoesDaSimulacao {
  /** Rascunho do GET; null = 404 (ainda sem rascunho). */
  cadastro?: CadastroDoProfissional | null;
  conta?: Partial<MinhaConta>;
  /** Resposta de cada escrita (PUT, POST, DELETE); sem ela (undefined), devolve o rascunho atual. */
  escrita?: (pedido: Pedido) => Resposta | undefined;
}

/** API do cadastro simulada: GETs de leitura e escritas que devolvem o rascunho atual. */
export function simularCadastro(opcoes: OpcoesDaSimulacao = {}) {
  const estado = { cadastro: opcoes.cadastro === undefined ? CADASTRO_VAZIO : opcoes.cadastro };
  const simulacao = simularFetch((pedido) => {
    if (pedido.url === '/api/publico/catalogo') {
      return json(CATALOGO_PADRAO);
    }
    if (pedido.url === '/api/publico/regras') {
      return json(REGRAS_PADRAO);
    }
    if (pedido.url === '/api/contas/eu') {
      return json({ ...CONTA_PADRAO, celularConfirmado: true, ...opcoes.conta });
    }
    if (pedido.metodo === 'GET' && pedido.url === '/api/profissional/cadastro') {
      return estado.cadastro === null ? problema(404, 'nao-encontrado', 'Cadastro não encontrado.') : json(estado.cadastro);
    }
    const propria = opcoes.escrita?.(pedido);
    if (propria !== undefined) {
      return propria;
    }
    estado.cadastro ??= CADASTRO_VAZIO;
    return json(estado.cadastro);
  });
  return {
    ...simulacao,
    /** Muda o que o GET (e as escritas sem resposta própria) devolvem dali em diante. */
    definir(cadastro: CadastroDoProfissional) {
      estado.cadastro = cadastro;
    },
    escritas: () => simulacao.pedidos.filter((pedido) => pedido.metodo !== 'GET'),
  };
}

/** Corpo JSON do pedido. */
export function corpoDe(pedido: Pedido | undefined): unknown {
  return JSON.parse(pedido?.corpo ?? 'null');
}
