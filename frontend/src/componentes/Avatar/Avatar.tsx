import estilos from './Avatar.module.css';

/** Tokens com contraste AA para texto branco (todas acima de 4,5:1). */
export const CORES_DO_AVATAR = ['carimbo', 'carimbo-2', 'tinta', 'tinta-2', 'ok', 'bad', 'warn'] as const;

export type CorDoAvatar = (typeof CORES_DO_AVATAR)[number];

/** Primeira letra das duas primeiras palavras, como o iniciais() do protótipo. */
export function iniciais(nome: string): string {
  return nome
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte.charAt(0))
    .join('');
}

/** O mesmo nome sempre cai na mesma cor (sem guardar nada). */
export function corDoAvatar(nome: string): CorDoAvatar {
  let soma = 0;
  for (let i = 0; i < nome.length; i += 1) {
    soma = (soma * 31 + nome.charCodeAt(i)) >>> 0;
  }
  return CORES_DO_AVATAR[soma % CORES_DO_AVATAR.length] as CorDoAvatar;
}

interface AvatarProps {
  nome: string;
  tamanho?: 'sm' | 'md' | 'lg';
}

/** Decorativo: o nome da pessoa sempre aparece em texto ao lado. */
export function Avatar({ nome, tamanho = 'md' }: AvatarProps) {
  return (
    <span className={estilos.avatar} data-tamanho={tamanho} data-cor={corDoAvatar(nome)} aria-hidden="true">
      {iniciais(nome)}
    </span>
  );
}
