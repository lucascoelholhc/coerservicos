import type { ErroDaApi } from '../../api/erros';
import { Botao } from '../Botao/Botao';
import { Nota } from '../Nota/Nota';
import estilos from './FalhaAoCarregar.module.css';

interface FalhaAoCarregarProps {
  erro: ErroDaApi;
  aoTentarDeNovo: () => void;
}

/** Erro ao buscar dados da API: a mensagem em português (anunciada) e "Tentar de novo". */
export function FalhaAoCarregar({ erro, aoTentarDeNovo }: FalhaAoCarregarProps) {
  return (
    <div className={estilos.falha}>
      <Nota tipo="erro" anunciar>
        {erro.message}
      </Nota>
      <Botao variante="contorno" tamanho="sm" onClick={aoTentarDeNovo}>
        Tentar de novo
      </Botao>
    </div>
  );
}
