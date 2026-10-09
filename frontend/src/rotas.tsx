import { Route, Routes } from 'react-router';

import { CAMINHOS } from './caminhos';
import { Moldura } from './layout/Moldura';
import { CriarContaCliente } from './paginas/criar-conta/CriarContaCliente';
import { EscolhaDeConta } from './paginas/criar-conta/EscolhaDeConta';
import { Entrar } from './paginas/entrar/Entrar';
import { Inicio } from './paginas/inicio/Inicio';
import { NaoEncontrada } from './paginas/nao-encontrada/NaoEncontrada';

/** Todas as rotas do front. Rota que não existe cai em "Página não encontrada". */
export function Rotas() {
  return (
    <Routes>
      <Route element={<Moldura />}>
        <Route index element={<Inicio />} />
        <Route path={CAMINHOS.entrar} element={<Entrar />} />
        <Route path={CAMINHOS.criarConta} element={<EscolhaDeConta />} />
        <Route path={CAMINHOS.criarContaCliente} element={<CriarContaCliente />} />
        <Route path="*" element={<NaoEncontrada />} />
      </Route>
    </Routes>
  );
}
