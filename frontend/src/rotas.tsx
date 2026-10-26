import { Route, Routes } from 'react-router';

import { CAMINHOS } from './caminhos';
import { Moldura } from './layout/Moldura';
import { RotaPrivada } from './layout/RotaPrivada';
import { CodigoNoLogin } from './paginas/conta/CodigoNoLogin';
import { ConfirmarCelular } from './paginas/conta/ConfirmarCelular';
import { MinhaConta } from './paginas/conta/MinhaConta';
import { RecolocarContato } from './paginas/conta/RecolocarContato';
import { TrocarSenha } from './paginas/conta/TrocarSenha';
import { FilaDeVerificacao } from './paginas/admin/FilaDeVerificacao';
import { VerificacaoDoCadastro } from './paginas/admin/VerificacaoDoCadastro';
import { Busca } from './paginas/busca/Busca';
import { CadastroProfissional } from './paginas/cadastro/CadastroProfissional';
import { PerfilPublico } from './paginas/perfil/PerfilPublico';
import { Contratar } from './paginas/contratar/Contratar';
import { ContratoDoCliente } from './paginas/contratos/ContratoDoCliente';
import { MeusContratos } from './paginas/contratos/MeusContratos';
import { Conversa } from './paginas/mensagens/Conversa';
import { Conversar } from './paginas/mensagens/Conversar';
import { Mensagens } from './paginas/mensagens/Mensagens';
import { CriarContaCliente } from './paginas/criar-conta/CriarContaCliente';
import { CriarContaProfissional } from './paginas/criar-conta/CriarContaProfissional';
import { EscolhaDeConta } from './paginas/criar-conta/EscolhaDeConta';
import { Entrar } from './paginas/entrar/Entrar';
import { ConfirmarEmail } from './paginas/links/ConfirmarEmail';
import { ProvarEmail } from './paginas/links/ProvarEmail';
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
        <Route path={CAMINHOS.busca} element={<Busca />} />
        <Route path={`${CAMINHOS.perfil}/:id`} element={<PerfilPublico />} />
        <Route path={CAMINHOS.entrar} element={<Entrar />} />
        <Route path={CAMINHOS.criarConta} element={<EscolhaDeConta />} />
        <Route path={CAMINHOS.criarContaCliente} element={<CriarContaCliente />} />
        <Route path={CAMINHOS.criarContaProfissional} element={<CriarContaProfissional />} />
        <Route
          path={CAMINHOS.cadastro}
          element={
            <RotaPrivada>
              <CadastroProfissional />
            </RotaPrivada>
          }
        />
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
        <Route
          path={CAMINHOS.recolocarContato}
          element={
            <RotaPrivada>
              <RecolocarContato />
            </RotaPrivada>
          }
        />
        <Route
          path={CAMINHOS.adminVerificacao}
          element={
            <RotaPrivada papeis={['ADMIN']}>
              <FilaDeVerificacao />
            </RotaPrivada>
          }
        />
        <Route
          path={`${CAMINHOS.adminVerificacao}/:id`}
          element={
            <RotaPrivada papeis={['ADMIN']}>
              <VerificacaoDoCadastro />
            </RotaPrivada>
          }
        />
        <Route
          path={`${CAMINHOS.contratar}/:id`}
          element={
            <RotaPrivada papeis={['CLIENTE']}>
              <Contratar />
            </RotaPrivada>
          }
        />
        <Route
          path={CAMINHOS.contratos}
          element={
            <RotaPrivada papeis={['CLIENTE']}>
              <MeusContratos />
            </RotaPrivada>
          }
        />
        <Route
          path={`${CAMINHOS.contratos}/:id`}
          element={
            <RotaPrivada papeis={['CLIENTE']}>
              <ContratoDoCliente />
            </RotaPrivada>
          }
        />
        <Route
          path={CAMINHOS.mensagens}
          element={
            <RotaPrivada>
              <Mensagens />
            </RotaPrivada>
          }
        />
        <Route
          path={`${CAMINHOS.mensagens}/:id`}
          element={
            <RotaPrivada>
              <Conversa />
            </RotaPrivada>
          }
        />
        <Route
          path={`${CAMINHOS.conversar}/:perfil`}
          element={
            <RotaPrivada>
              <Conversar />
            </RotaPrivada>
          }
        />
        <Route path={CAMINHOS.confirmarEmail} element={<ConfirmarEmail />} />
        <Route path={CAMINHOS.provarEmail} element={<ProvarEmail />} />
        <Route path={CAMINHOS.esqueciSenha} element={<EsqueciSenha />} />
        <Route path={CAMINHOS.redefinirSenha} element={<RedefinirSenha />} />
        <Route path={CAMINHOS.termos} element={<Termos />} />
        <Route path={CAMINHOS.privacidade} element={<Privacidade />} />
        <Route path="*" element={<NaoEncontrada />} />
      </Route>
    </Routes>
  );
}
