import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { PerfilPublico as Perfil } from '../../api/tipos';
import { semViolacoes } from '../../teste/axe';
import { CATALOGO_PADRAO, REGRAS_PADRAO } from '../../teste/dadosDaApi';
import { PERFIL_DE_EXEMPLO } from '../../teste/dadosDaBusca';
import { json, problema, simularFetch } from '../../teste/fetchSimulado';
import { PerfilPublico } from './PerfilPublico';

const ID = PERFIL_DE_EXEMPLO.id;

function OndeEstou() {
  const local = useLocation();
  return <p>Voltou para {`${local.pathname}${local.search}`}</p>;
}

function abrir(estado?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: `/profissionais/${ID}`, state: estado }]}>
      <Routes>
        <Route path="/profissionais/:id" element={<PerfilPublico />} />
        <Route path="/busca" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

function simular(perfil: () => Response = () => json(PERFIL_DE_EXEMPLO), regras: unknown = REGRAS_PADRAO) {
  return simularFetch((pedido) => {
    if (pedido.url === `/api/publico/profissionais/${ID}`) {
      return perfil();
    }
    if (pedido.url === '/api/publico/catalogo') {
      return json(CATALOGO_PADRAO);
    }
    if (pedido.url === '/api/publico/regras') {
      return json(regras);
    }
    return problema(404, 'nao-encontrado', 'x');
  });
}

const comPerfil = (troca: Partial<Perfil>) => () => json({ ...PERFIL_DE_EXEMPLO, ...troca });

describe('Perfil público', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-23T15:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('mostra o perfil como a API manda, sem violação de acessibilidade', async () => {
    simular();
    const { container } = abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'Valdir S.' })).toBeInTheDocument();
    expect(document.title).toBe('Valdir S. · COE Serviços');
    expect(await screen.findByText('Pedreiro e pintor')).toBeInTheDocument();
    expect(screen.getByText('Documento conferido')).toBeInTheDocument();
    expect(screen.queryByText('NR-10 conferido')).not.toBeInTheDocument();
    expect(screen.getAllByText('Novo na COE').length).toBeGreaterThan(0);
    const fatos = screen.getByRole('list', { name: 'Resumo' });
    expect(within(fatos).getByText('20 anos')).toBeInTheDocument();
    expect(within(fatos).getByText('7h às 17h')).toBeInTheDocument();
    expect(within(fatos).getByText('Leva as suas')).toBeInTheDocument();
    expect(within(fatos).getByText('15 km')).toBeInTheDocument();
    expect(screen.getByText('Trabalho com capricho. Faço muro e calçada.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'O que faz' }).parentElement).toHaveTextContent(
      'AlvenariaContrapisoPintura interna',
    );
    expect(screen.getByText('Mora em Blumenau e vai até 15 km.')).toBeInTheDocument();
    expect(screen.getByText('Gaspar')).toBeInTheDocument();
    expect(screen.getByText('Valdir ainda não tem avaliações')).toBeInTheDocument();
    await semViolacoes(container);
  });

  it('foto do trabalho pela URL assinada do bucket, com a legenda', async () => {
    simular();
    abrir();

    const foto = await screen.findByRole('img', { name: 'Muro de bloco' });
    expect(foto).toHaveAttribute('src', PERFIL_DE_EXEMPLO.fotos[0]?.url);
  });

  it('foto que não abre (URL vencida ou arquivo fora) vira aviso, sem quebrar a tela', async () => {
    simular();
    abrir();

    fireEvent.error(await screen.findByRole('img', { name: 'Muro de bloco' }));

    expect(screen.queryByRole('img', { name: 'Muro de bloco' })).not.toBeInTheDocument();
    expect(screen.getByText('Foto indisponível agora. Recarregue a página.')).toBeInTheDocument();
  });

  it('endereço de foto que não é http(s) nunca vira imagem', async () => {
    simular(comPerfil({ fotos: [{ id: 'x', servico: null, legenda: 'Ruim', url: 'javascript:alert(1)', expiraEm: '' }] }));
    abrir();

    await screen.findByRole('heading', { level: 1, name: 'Valdir S.' });
    expect(screen.queryByRole('img', { name: 'Ruim' })).not.toBeInTheDocument();
  });

  it('sem fotos: diz que ainda não há', async () => {
    simular(comPerfil({ fotos: [] }));
    abrir();

    expect(await screen.findByText('Ainda sem fotos de trabalhos.')).toBeInTheDocument();
  });

  it('dias livres num calendário com o estado escrito em cada dia e legenda', async () => {
    simular();
    abrir();

    const calendario = await screen.findByRole('group', { name: 'Dias livres das próximas 4 semanas' });
    expect(within(calendario).getByRole('img', { name: 'segunda-feira, 26 de outubro: livre' })).toBeInTheDocument();
    expect(within(calendario).getByRole('img', { name: 'quarta-feira, 28 de outubro: ocupado' })).toBeInTheDocument();
    expect(
      within(calendario).getByRole('img', { name: 'sábado, 24 de outubro: não trabalha neste dia' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Livre', { selector: 'span' })).toBeInTheDocument();
  });

  it('caixa de preço: diária, meia diária, taxa das regras e o primeiro dia livre, sem contato', async () => {
    simular();
    abrir();

    const caixa = await screen.findByRole('complementary', { name: 'Valor e contratação' });
    expect(within(caixa).getByText('R$ 280,00')).toBeInTheDocument();
    expect(within(caixa).getByText('R$ 150,00')).toBeInTheDocument();
    expect(await within(caixa).findByText('+ taxa de 10% no pagamento')).toBeInTheDocument();
    expect(within(caixa).getByText('Livre seg, 26/10')).toBeInTheDocument();
    expect(within(caixa).getByText('Telefone e WhatsApp aparecem depois de contratar.')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/\(\d{2}\)|@|whatsapp\.com|\d{4,5}-\d{4}/i);
  });

  it('taxa paga pelo profissional: "Taxa já incluída"', async () => {
    simular(undefined, { ...REGRAS_PADRAO, taxaPagaPor: 'PROFISSIONAL' });
    abrir();

    expect(await screen.findByText('Taxa já incluída')).toBeInTheDocument();
  });

  it('com avaliações: nota e quantas', async () => {
    simular(comPerfil({ avaliacoes: { total: 12, nota: 4.8, novoNaCoe: false }, seloNr10: true }));
    abrir();

    expect(await screen.findByText('NR-10 conferido')).toBeInTheDocument();
    expect(screen.getAllByText('4,8').length).toBeGreaterThan(0);
    expect(screen.queryByText('Novo na COE')).not.toBeInTheDocument();
  });

  it('"Voltar para a busca" volta para a busca de onde veio', async () => {
    simular();
    abrir({ busca: 'profissao=pedreiro&cidade=4202404' });

    await userEvent.click(await screen.findByRole('link', { name: 'Voltar para a busca' }));

    expect(screen.getByText('Voltou para /busca?profissao=pedreiro&cidade=4202404')).toBeInTheDocument();
  });

  it('busca de onde veio que não é texto de busca é ignorada', async () => {
    simular();
    abrir({ busca: '//outro.site' });

    await userEvent.click(await screen.findByRole('link', { name: 'Voltar para a busca' }));

    expect(screen.getByText('Voltou para /busca')).toBeInTheDocument();
  });

  it('404: "Profissional não encontrado" e caminho para a busca', async () => {
    simular(() => problema(404, 'nao-encontrado', 'Profissional não encontrado.'));
    abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'Profissional não encontrado' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver outros profissionais' })).toHaveAttribute('href', '/busca');
  });

  it('erro: mensagem e "Tentar de novo"', async () => {
    let falhar = true;
    simular(() => (falhar ? problema(500, 'erro-interno', 'x') : json(PERFIL_DE_EXEMPLO)));
    abrir();

    expect(await screen.findByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
    falhar = false;
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Valdir S.' })).toBeInTheDocument();
  });
});
