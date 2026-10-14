import { Link } from 'react-router';

import { CAMINHOS } from '../../caminhos';
import { useTitulo } from '../../layout/titulo';
import estilos from './CriarConta.module.css';
import { FormularioDoCliente } from './FormularioDoCliente';

/** Cadastro de cliente (RF03): a página com o formulário. */
export function CriarContaCliente() {
  useTitulo('Criar conta');
  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <Link className={estilos.voltar} to={CAMINHOS.criarConta}>
          Voltar
        </Link>
        <header className={estilos.topo}>
          <p className={estilos.chamada}>Conta de cliente</p>
          <h1>Criar conta para contratar</h1>
          <p className={estilos.apagado}>Só o básico. O endereço completo você informa na hora de contratar.</p>
        </header>
        <FormularioDoCliente />
        <p className={estilos.rodape}>
          Quer oferecer seus serviços? <Link to={CAMINHOS.paraProfissionais}>Cadastro de profissional</Link>
        </p>
      </div>
    </section>
  );
}
