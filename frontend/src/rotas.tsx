import { Route, Routes } from 'react-router';

import { CAMINHOS } from './caminhos';
import { Moldura } from './layout/Moldura';
import { RotaPrivada } from './layout/RotaPrivada';
import { CodigoNoLogin } from './paginas/conta/CodigoNoLogin';
import { ConfirmarCelular } from './paginas/conta/ConfirmarCelular';
import { MinhaConta } from './paginas/conta/MinhaConta';
import { TrocarSenha } from './paginas/conta/TrocarSenha';
import { CriarContaCliente } from './paginas/criar-conta/CriarContaCliente';
import { EscolhaDeConta } from './paginas/criar-conta/EscolhaDeConta';
import { Entrar } from './paginas/entrar/Entrar';
import { ConfirmarEmail } from './paginas/links/ConfirmarEmail';
import { EsqueciSenha } from './paginas/senha/EsqueciSenha';
import { RedefinirSenha } from './paginas/senha/RedefinirSenha';
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
        <Route
          path={CAMINHOS.confirmarCelular}
          element={
            <RotaPrivada>
              <ConfirmarCelular />
            </RotaPrivada>
          }
        />
        <Route
          path={CAMINHOS.codigoNoLogin}
          element={
            <RotaPrivada>
              <CodigoNoLogin />
            </RotaPrivada>
          }
        />
        <Route
          path={CAMINHOS.trocarSenha}
          element={
            <RotaPrivada>
              <TrocarSenha />
            </RotaPrivada>
          }
        />
        <Route path={CAMINHOS.confirmarEmail} element={<ConfirmarEmail />} />
        <Route path={CAMINHOS.esqueciSenha} element={<EsqueciSenha />} />
        <Route path={CAMINHOS.redefinirSenha} element={<RedefinirSenha />} />
        <Route path={CAMINHOS.termos} element={<Termos />} />
        <Route path={CAMINHOS.privacidade} element={<Privacidade />} />
        <Route path="*" element={<NaoEncontrada />} />
      </Route>
    </Routes>
  );
}
