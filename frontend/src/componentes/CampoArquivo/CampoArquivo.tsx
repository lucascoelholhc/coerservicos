import { useId, type ReactNode } from 'react';

import { Icone } from '../../icones/Icone';
import estilos from './CampoArquivo.module.css';

/** Os tipos que o servidor aceita (o ValidadorDeImagem confere o conteúdo; HEIC e PDF são recusados). */
export const TIPOS_DE_IMAGEM = 'image/jpeg,image/png,image/webp';

interface CampoArquivoProps {
  rotulo: string;
  dica?: string;
  /** O que o botão faz ("Tirar selfie", "Adicionar fotos"). */
  botao: string;
  /** Câmera da frente (selfie) ou de trás (documento); sem ela, o celular oferece câmera ou galeria. */
  captura?: 'user' | 'environment';
  varios?: boolean;
  /** Botão grande no azul do carimbo (a ação da etapa). */
  grande?: boolean;
  desabilitado?: boolean;
  erro?: string;
  aoEscolher: (arquivos: File[]) => void;
  /** Situação e miniaturas do que já foi mandado. */
  children?: ReactNode;
}

/** Foto pela câmera ou pela galeria (protótipo: uplField). O input nativo fica escondido sob o botão. */
export function CampoArquivo({
  rotulo,
  dica,
  botao,
  captura,
  varios = false,
  grande = false,
  desabilitado = false,
  erro,
  aoEscolher,
  children,
}: CampoArquivoProps) {
  const id = useId();
  const idDoRotulo = `${id}-rotulo`;
  const idDoBotao = `${id}-botao`;
  const descricao = [erro ? `${id}-erro` : '', dica ? `${id}-dica` : ''].filter(Boolean).join(' ');
  return (
    <div className={estilos.campo}>
      <span className={estilos.rotulo} id={idDoRotulo}>
        {rotulo}
      </span>
      {dica && (
        <p className={estilos.dica} id={`${id}-dica`}>
          {dica}
        </p>
      )}
      <label className={estilos.botao} data-grande={grande} data-desabilitado={desabilitado} htmlFor={id}>
        <Icone nome="camera" tamanho={grande ? 44 : 30} className={estilos.icone} />
        <span>
          <b id={idDoBotao}>{botao}</b>
          <small>Abre a câmera ou a galeria do celular</small>
        </span>
      </label>
      <input
        className="sr-only"
        type="file"
        id={id}
        accept={TIPOS_DE_IMAGEM}
        capture={captura}
        multiple={varios}
        disabled={desabilitado}
        aria-labelledby={`${idDoRotulo} ${idDoBotao}`}
        aria-describedby={descricao || undefined}
        aria-invalid={erro ? true : undefined}
        onChange={(evento) => {
          const arquivos = Array.from(evento.target.files ?? []);
          // Limpa para a mesma foto poder ser escolhida de novo (ex.: depois de um erro).
          evento.target.value = '';
          if (arquivos.length > 0) {
            aoEscolher(arquivos);
          }
        }}
      />
      {children}
      {erro && (
        <p className={estilos.erro} id={`${id}-erro`}>
          {erro}
        </p>
      )}
    </div>
  );
}
