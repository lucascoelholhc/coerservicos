import { Link } from 'react-router';

import { CAMINHOS } from '../../caminhos';
import { Ilustracao } from '../../componentes/Ilustracao/Ilustracao';
import { useTitulo } from '../../layout/titulo';
import estilos from './EscolhaDeConta.module.css';

/** Primeiro a pessoa diz se quer contratar ou trabalhar (protótipo, V.criarConta). */
export function EscolhaDeConta() {
  useTitulo('Criar conta');
  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <h1>Criar conta</h1>
        <p className={estilos.apagado}>Como você vai usar a COE?</p>
      </header>
      <div className={estilos.opcoes}>
        <Link className={estilos.opcao} to={CAMINHOS.criarContaCliente}>
          <Ilustracao profissao="pedreiro" />
          <span className={estilos.texto}>
            <b>Quero contratar</b>
            <span>Encontrar e pagar pedreiro, pintor, eletricista, jardineiro ou diarista.</span>
            <span className={estilos.chamada}>Criar conta de cliente</span>
          </span>
        </Link>
        <Link className={estilos.opcao} to={CAMINHOS.paraProfissionais}>
          <Ilustracao profissao="pintor" />
          <span className={estilos.texto}>
            <b>Quero trabalhar</b>
            <span>Oferecer meus serviços e receber pelo app. Cadastro grátis.</span>
            <span className={estilos.chamada}>Cadastro de profissional</span>
          </span>
        </Link>
      </div>
      <p className={estilos.rodape}>
        Já tem conta? <Link to={CAMINHOS.entrar}>Entrar</Link>
      </p>
    </section>
  );
}
