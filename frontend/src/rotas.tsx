import { Route, Routes } from 'react-router';

import { CAMINHOS } from './caminhos';
import { Moldura } from './layout/Moldura';
import { RotaPrivada } from './layout/RotaPrivada';
import { MinhaConta } from './paginas/conta/MinhaConta';
import { CriarContaCliente } from './paginas/criar-conta/CriarContaCliente';
import { EscolhaDeConta } from './paginas/criar-conta/EscolhaDeConta';
import { Entrar } from './paginas/entrar/Entrar';
import { Privacidade, Termos } from './paginas/termos/Rascunhos';
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
        <Route
          path={CAMINHOS.conta}
          element={
            <RotaPrivada>
              <MinhaConta />
            </RotaPrivada>
          }
        />
        <Route path={CAMINHOS.termos} element={<Termos />} />
        <Route path={CAMINHOS.privacidade} element={<Privacidade />} />
        <Route path="*" element={<NaoEncontrada />} />
      </Route>
    </Routes>
  );
}
