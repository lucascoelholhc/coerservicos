import { profissoesDoCatalogo } from '../../api/catalogo';
import type { CadastroDoProfissional, Catalogo, EtapaDoCadastro } from '../../api/tipos';
import { formatarReais } from '../../formatacao/dinheiro';
import { DIAS_DA_SEMANA, EXPERIENCIA } from './conteudo';

/** Uma linha por etapa com o que está salvo (revisão antes de enviar e conferência do admin). */
export function resumo(cadastro: CadastroDoProfissional, catalogo: Catalogo): Record<EtapaDoCadastro, string> {
  const profissoes = profissoesDoCatalogo(catalogo);
  const nomeDaProfissao = (codigo: string) => profissoes.find((p) => p.codigo === codigo)?.nome ?? codigo;
  const nomeDaCidade = (codigo: number) => catalogo.cidades.find((c) => c.codigoIbge === codigo)?.nome ?? String(codigo);
  const trabalho = cadastro.profissao
    ? [cadastro.profissao, ...cadastro.outrasProfissoes].map(nomeDaProfissao).join(', ')
    : '';
  const valor = cadastro.valorDiaria
    ? `${formatarReais(cadastro.valorDiaria)}${cadastro.valorMeiaDiaria ? ` · meia ${formatarReais(cadastro.valorMeiaDiaria)}` : ''}`
    : '';
  const onde = cadastro.cidade
    ? [
        nomeDaCidade(cadastro.cidade),
        cadastro.raioKm ? `até ${cadastro.raioKm} km` : '',
        cadastro.outrasCidades.length > 0 ? `+ ${cadastro.outrasCidades.map(nomeDaCidade).join(', ')}` : '',
      ]
        .filter(Boolean)
        .join(' · ')
    : '';
  const fotos = cadastro.fotos.length;
  return {
    trabalho,
    dados: cadastro.cpf ? `CPF ${cadastro.cpf}` : '',
    documentos: cadastro.etapas.documentos ? 'Documento e selfie enviados' : '',
    servicos: cadastro.servicos.map((s) => s.nome).join(', '),
    experiencia: EXPERIENCIA.find((faixa) => faixa.anos === cadastro.experienciaAnos)?.texto ?? '',
    valor,
    onde,
    dias: DIAS_DA_SEMANA.filter((d) => cadastro.dias.includes(d.dia))
      .map((d) => d.curto)
      .join(', '),
    fotos: fotos === 0 ? '' : fotos === 1 ? '1 foto' : `${fotos} fotos`,
    sobre: cadastro.etapas.sobre ? 'Pronto' : '',
    pix: cadastro.chavePix ?? '',
  };
}
