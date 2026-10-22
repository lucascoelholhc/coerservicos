import { Link, Navigate } from 'react-router';

import { useSessao } from '../../api/SessaoProvider';
import { caminhoDeEntrar } from '../../api/voltar';
import { CAMINHOS } from '../../caminhos';
import { useTitulo } from '../../layout/titulo';
import estilos from './CriarConta.module.css';
import { FormularioDoCliente } from './FormularioDoCliente';

/**
 * "Quero trabalhar" (FE-04): o profissional cria a mesma conta do cliente (nome, celular, e-mail, CEP e
 * senha) e segue para o cadastro do trabalho. O papel PROFISSIONAL só vem na aprovação (DOM-03).
 */
export function CriarContaProfissional() {
  useTitulo('Criar conta para trabalhar');
  const usuario = useSessao();
  if (usuario) {
    return <Navigate replace to={CAMINHOS.cadastro} />;
  }
  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <Link className={estilos.voltar} to={CAMINHOS.criarConta}>
          Voltar
        </Link>
        <header className={estilos.topo}>
          <p className={estilos.chamada}>Cadastro de profissional</p>
          <h1>Criar conta para trabalhar</h1>
          <p className={estilos.apagado}>
            Primeiro, sua conta: nome, celular, e-mail e senha. Depois você conta do seu trabalho, uma pergunta por vez.
            O cadastro é grátis.
          </p>
        </header>
        <FormularioDoCliente destino={CAMINHOS.cadastro} />
        <p className={estilos.rodape}>
          Já tem conta? <Link to={caminhoDeEntrar(CAMINHOS.cadastro)}>Entrar</Link>
        </p>
      </div>
    </section>
  );
}
