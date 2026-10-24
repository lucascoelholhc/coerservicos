import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CampoArquivo } from './CampoArquivo';

describe('CampoArquivo', () => {
  it('abre a câmera ou a galeria e entrega os arquivos escolhidos', async () => {
    const aoEscolher = vi.fn();
    const { container } = render(
      <CampoArquivo rotulo="Selfie segurando o documento" dica="Rosto e documento juntos." botao="Tirar selfie" captura="user" aoEscolher={aoEscolher} />,
    );
    const campo = screen.getByLabelText(/Selfie segurando o documento/);

    expect(campo).toHaveAttribute('type', 'file');
    expect(campo).toHaveAttribute('capture', 'user');
    expect(campo).toHaveAttribute('accept', 'image/jpeg,image/png,image/webp');
    expect(campo).toHaveAccessibleDescription('Rosto e documento juntos.');
    await userEvent.upload(campo, new File(['x'], 'eu.jpg', { type: 'image/jpeg' }));

    expect(aoEscolher).toHaveBeenCalledWith([expect.objectContaining({ name: 'eu.jpg' })]);
    expect(screen.getByText('Abre a câmera do celular')).toBeInTheDocument();
    expect(campo).toHaveValue('');
    await semViolacoes(container);
  });

  it('várias fotos de uma vez; erro ligado ao campo', async () => {
    const aoEscolher = vi.fn();
    render(<CampoArquivo rotulo="Fotos" botao="Adicionar fotos" varios aoEscolher={aoEscolher} erro="Mande pelo menos 1 foto." />);
    const campo = screen.getByLabelText(/Fotos/);

    await userEvent.upload(campo, [new File(['a'], 'a.jpg', { type: 'image/jpeg' }), new File(['b'], 'b.png', { type: 'image/png' })]);

    expect(campo).toHaveAttribute('multiple');
    expect(screen.getByText('Abre a câmera ou a galeria do celular')).toBeInTheDocument();
    expect(campo).not.toHaveAttribute('capture');
    expect(aoEscolher.mock.calls[0]?.[0]).toHaveLength(2);
    expect(campo).toHaveAccessibleDescription('Mande pelo menos 1 foto.');
  });

  it('desabilitado enquanto manda', () => {
    render(<CampoArquivo rotulo="Fotos" botao="Mandando…" desabilitado aoEscolher={() => undefined} />);

    expect(screen.getByLabelText(/Fotos/)).toBeDisabled();
  });
});
