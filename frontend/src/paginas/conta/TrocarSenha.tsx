import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { trocarSenha } from '../../api/autenticacao';
import type { ErroDaApi } from '../../api/erros';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSenha } from '../../componentes/CampoSenha/CampoSenha';
import { Nota } from '../../componentes/Nota/Nota';
import estilos from '../../layout/PaginaDeFormulario.module.css';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from './useEnvioUnico';

type Erros = { atual?: string; nova?: string };

const SENHA_MINIMA = 8;
const DICA_DA_SENHA = 'Use pelo menos 8 caracteres. Não use seu celular nem seu e-mail.';

/** Trocar a senha logado (privada): a sessão deste aparelho continua, as outras saem. */
export function TrocarSenha() {
  useTitulo('Trocar senha');
  const navegar = useNavigate();
  const { enviando, executar } = useEnvioUnico();
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erro, setErro] = useState<string | null>(null);

  function avisos(): Erros {
    const encontrados: Erros = {};
    if (!atual) {
      encontrados.atual = 'Informe a senha atual.';
    }
    if (nova.length < SENHA_MINIMA) {
      encontrados.nova = 'A senha precisa ter pelo menos 8 caracteres.';
    }
    return encontrados;
  }

  function tratarErro(problema: ErroDaApi) {
    if (problema.codigo === 'senha-atual-incorreta') {
      setErros({ atual: problema.message });
    } else if (problema.codigo === 'senha-nao-permitida') {
      setErros({ nova: problema.message });
    } else {
      setErro(problema.message);
    }
  }

  function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (enviando) {
      return;
    }
    setErro(null);
    const encontrados = avisos();
    setErros(encontrados);
    if (Object.keys(encontrados).length > 0) {
      return;
    }
    void executar(async () => {
      await trocarSenha({ senhaAtual: atual, novaSenha: nova });
      // As senhas não ficam na memória da tela depois do sucesso.
      setAtual('');
      setNova('');
      void navegar(CAMINHOS.conta, {
        replace: true,
        state: { aviso: 'Senha trocada. Os outros aparelhos foram desconectados.' },
      });
    }, tratarErro);
  }

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>Trocar senha</h1>
          <p className={estilos.apagado}>Depois da troca, os outros aparelhos saem da sua conta. Este continua.</p>
        </header>
        <form className={estilos.cartao} onSubmit={enviar} noValidate>
          <CampoSenha
            nome="senhaAtual"
            rotulo="Senha atual"
            valor={atual}
            aoMudar={setAtual}
            autoComplete="current-password"
            erro={erros.atual}
          />
          <CampoSenha
            nome="novaSenha"
            rotulo="Nova senha"
            valor={nova}
            aoMudar={setNova}
            autoComplete="new-password"
            dica={DICA_DA_SENHA}
            erro={erros.nova}
          />
          {erro && (
            <Nota tipo="erro" anunciar>
              {erro}
            </Nota>
          )}
          <Botao type="submit" tamanho="lg" desabilitado={enviando}>
            {enviando ? 'Salvando…' : 'Trocar senha'}
          </Botao>
        </form>
        <Link className={estilos.link} to={CAMINHOS.conta}>
          Voltar para minha conta
        </Link>
      </div>
    </section>
  );
}
