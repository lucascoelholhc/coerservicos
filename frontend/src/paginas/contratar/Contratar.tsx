import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { buscarPerfilPublico } from '../../api/busca';
import { buscarCatalogo } from '../../api/catalogo';
import { criarContrato } from '../../api/contratos';
import { buscarRegras } from '../../api/regras';
import type { Catalogo, NovoContrato, PerfilPublico, Regras } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { caminhoDoContrato, caminhoDoPerfil } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSelecao } from '../../componentes/CampoSelecao/CampoSelecao';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { CampoTextoLongo } from '../../componentes/CampoTextoLongo/CampoTextoLongo';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { Opcoes } from '../../componentes/Opcoes/Opcoes';
import { formatarDiaCurto } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { umaSo } from '../cadastro/etapas/comum';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Contratar.module.css';

interface Dados {
  perfil: PerfilPublico;
  catalogo: Catalogo;
  regras: Regras;
}

/** Erros da API que dizem respeito aos dias: a tela volta para a escolha dos dias e mostra a frase. */
const ERROS_DOS_DIAS = new Set([
  'lc150-limite',
  'dia-indisponivel',
  'dia-fora-da-agenda',
  'dia-fora-da-janela',
  'dia-repetido',
  'meia-diaria-indisponivel',
]);

/** Campos com erro mostrado no próprio campo, na etapa do pedido. */
const CAMPOS_DO_PEDIDO = new Set([
  'descricao',
  'material',
  'cep',
  'cidade',
  'logradouro',
  'numero',
  'bairro',
  'complemento',
]);

const MATERIAIS: { valor: NovoContrato['material']; texto: string }[] = [
  { valor: 'cliente_tem', texto: 'Já tenho o material' },
  { valor: 'cliente_compra', texto: 'Vou comprar: o profissional me diz o que falta' },
  { valor: 'combinar', texto: 'Ainda não sei, combino pelo chat' },
];

async function carregar(id: string, sinal: AbortSignal): Promise<Dados> {
  const [perfil, catalogo, regras] = await Promise.all([
    buscarPerfilPublico(id, sinal),
    buscarCatalogo(sinal),
    buscarRegras(sinal),
  ]);
  return { perfil, catalogo, regras };
}

/** Contratar (FE-05, protótipo V.contratar): 1. dias; 2. o pedido e o endereço. O valor final sai da API. */
export function Contratar() {
  const { id = '' } = useParams();
  const buscar = useCallback((sinal: AbortSignal) => carregar(id, sinal), [id]);
  const recurso = useRecurso(buscar, undefined, id);
  useTitulo('Contratar');

  return (
    <section className={`largura ${estilos.pagina}`}>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Formulario dados={recurso.dados} />}
    </section>
  );
}

function Formulario({ dados }: { dados: Dados }) {
  const { perfil, catalogo, regras } = dados;
  const navegar = useNavigate();
  const { enviando, executar } = useEnvioUnico();
  const [etapa, setEtapa] = useState<1 | 2>(1);
  const [tipo, setTipo] = useState<'inteira' | 'meia'>('inteira');
  const [dias, setDias] = useState<string[]>([]);
  const [servicos, setServicos] = useState<string[]>([]);
  const [descricao, setDescricao] = useState('');
  const [material, setMaterial] = useState<string[]>([]);
  const [cep, setCep] = useState('');
  const [cidade, setCidade] = useState(String(perfil.cidade.codigoIbge));
  const [logradouro, setLogradouro] = useState('');
  const [numero, setNumero] = useState('');
  const [bairro, setBairro] = useState('');
  const [complemento, setComplemento] = useState('');
  const [erroDosDias, setErroDosDias] = useState<string | null>(null);
  const [erros, setErros] = useState<Record<string, string>>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const titulo = useRef<HTMLHeadingElement>(null);
  const etapaAnterior = useRef(etapa);
  const primeiro = perfil.nome.split(' ')[0];
  const valorDoDia = tipo === 'meia' && perfil.valorMeiaDiaria ? perfil.valorMeiaDiaria : perfil.valorDiaria;

  // troca de etapa: o foco vai para o título novo (leitor de tela recomeça ali); na abertura, não mexe
  useEffect(() => {
    if (etapaAnterior.current !== etapa) {
      titulo.current?.focus();
    }
    etapaAnterior.current = etapa;
  }, [etapa]);

  function continuar() {
    if (dias.length === 0) {
      setErroDosDias('Escolha pelo menos um dia.');
      return;
    }
    setErroDosDias(null);
    setEtapa(2);
  }

  function faltandoNoPedido(): Record<string, string> {
    const faltando: Record<string, string> = {};
    if (cep.replace(/\D/g, '').length !== 8) {
      faltando.cep = 'Digite o CEP com 8 números.';
    }
    if (logradouro.trim() === '') {
      faltando.logradouro = 'Diga a rua.';
    }
    if (numero.trim() === '') {
      faltando.numero = 'Diga o número.';
    }
    if (material.length === 0) {
      faltando.material = 'Escolha uma opção.';
    }
    return faltando;
  }

  function montarPedido(): NovoContrato {
    return {
      profissionalId: perfil.id,
      dias: [...dias].sort().map((data) => ({ data, tipo })),
      servicos,
      ...(descricao.trim() === '' ? {} : { descricao: descricao.trim() }),
      material: material[0] as NovoContrato['material'],
      endereco: {
        cep: cep.replace(/\D/g, ''),
        cidade: Number(cidade),
        logradouro: logradouro.trim(),
        numero: numero.trim(),
        ...(bairro.trim() === '' ? {} : { bairro: bairro.trim() }),
        ...(complemento.trim() === '' ? {} : { complemento: complemento.trim() }),
      },
      versaoTermosAceita: regras.versaoTermos,
    };
  }

  function enviar() {
    const faltando = faltandoNoPedido();
    setErros(faltando);
    setErroGeral(null);
    if (Object.keys(faltando).length > 0) {
      return;
    }
    void executar(
      async () => {
        const contrato = await criarContrato(montarPedido());
        void navegar(caminhoDoContrato(contrato.id), { state: { novo: true } });
      },
      (erro) => {
        if (erro.codigo && ERROS_DOS_DIAS.has(erro.codigo)) {
          setErroDosDias(erro.message);
          setEtapa(1);
          return;
        }
        const porCampo: Record<string, string> = {};
        for (const [campo, mensagem] of Object.entries(erro.campos)) {
          porCampo[campo.replace(/^endereco\./, '')] = mensagem;
        }
        setErros(porCampo);
        // erro de um campo que não está nesta etapa também aparece: nada falha em silêncio
        const algumVisivel = Object.keys(porCampo).some((campo) => CAMPOS_DO_PEDIDO.has(campo));
        setErroGeral(algumVisivel ? null : erro.message);
      },
    );
  }

  const cabecalho = (
    <header className={estilos.topo}>
      <Link className={estilos.voltar} to={caminhoDoPerfil(perfil.id)}>
        <Icone nome="back" tamanho={18} />
        Perfil de {primeiro}
      </Link>
      <ol className={estilos.passos} aria-label="Etapas">
        <li aria-current={etapa === 1 ? 'step' : undefined}>1. Dias</li>
        <li aria-current={etapa === 2 ? 'step' : undefined}>2. Pedido</li>
        <li>3. Pagamento</li>
      </ol>
      <p className={estilos.quem}>
        <b>{perfil.nome}</b>
        <span>
          {formatarReais(valorDoDia)} {tipo === 'meia' ? 'a meia diária' : 'a diária'}
        </span>
      </p>
    </header>
  );

  if (etapa === 1) {
    return (
      <div className={estilos.etapa}>
        {cabecalho}
        <h1 ref={titulo} tabIndex={-1}>
          Escolha os dias
        </h1>
        {perfil.valorMeiaDiaria && (
          <Opcoes
            legenda="Período"
            nome="periodo"
            tipo="unica"
            forma="cartao"
            opcoes={[
              {
                valor: 'inteira',
                texto: 'Diária inteira',
                detalhe: `${perfil.horario} · ${formatarReais(perfil.valorDiaria)}`,
              },
              { valor: 'meia', texto: 'Meia diária', detalhe: formatarReais(perfil.valorMeiaDiaria) },
            ]}
            selecionadas={[tipo]}
            aoMudar={umaSo((valor) => setTipo(valor as 'inteira' | 'meia'))}
          />
        )}
        {perfil.diasLivres.length === 0 ? (
          <Nota>{primeiro} não tem dias livres nas próximas 4 semanas.</Nota>
        ) : (
          <Opcoes
            legenda="Dias livres nas próximas 4 semanas"
            nome="dias"
            tipo="varias"
            forma="pilula"
            opcoes={perfil.diasLivres.map((dia) => ({ valor: dia, texto: formatarDiaCurto(dia) }))}
            selecionadas={dias}
            aoMudar={setDias}
            dica="Toque nos dias para escolher. Toque de novo para desmarcar."
          />
        )}
        {erroDosDias && (
          <Nota tipo="atencao" anunciar>
            {erroDosDias}
          </Nota>
        )}
        <div className={estilos.acoes}>
          <p className={estilos.total}>
            <b>
              {dias.length} {dias.length === 1 ? 'diária' : 'diárias'}
            </b>
            <span>O valor com a taxa aparece antes de pagar.</span>
          </p>
          <Botao onClick={continuar}>Continuar</Botao>
        </div>
      </div>
    );
  }

  return (
    <div className={estilos.etapa}>
      {cabecalho}
      <h1 ref={titulo} tabIndex={-1}>
        Conte o que precisa
      </h1>
      <p className={estilos.apoio}>Quanto mais {primeiro} souber antes, mais preparado chega no dia.</p>
      {perfil.servicos.length > 0 && (
        <Opcoes
          legenda="Toque no que você precisa"
          nome="servicos"
          tipo="varias"
          opcoes={perfil.servicos.map((s) => ({ valor: s.nome, texto: s.nome }))}
          selecionadas={servicos}
          aoMudar={setServicos}
        />
      )}
      <CampoTextoLongo
        nome="descricao"
        rotulo="Explique com suas palavras"
        valor={descricao}
        aoMudar={setDescricao}
        maxLength={600}
        dica="Sem telefone ou e-mail: o contato aparece depois do pagamento."
        erro={erros.descricao}
      />
      <Opcoes
        legenda="Material"
        nome="material"
        tipo="unica"
        forma="lista"
        opcoes={MATERIAIS}
        selecionadas={material}
        aoMudar={setMaterial}
        erro={erros.material}
      />
      <fieldset className={estilos.endereco}>
        <legend>Onde vai ser</legend>
        <CampoTexto
          nome="cep"
          rotulo="CEP"
          valor={cep}
          aoMudar={setCep}
          inputMode="numeric"
          autoComplete="postal-code"
          maxLength={9}
          erro={erros.cep}
        />
        <CampoSelecao
          nome="cidade"
          rotulo="Cidade"
          opcoes={catalogo.cidades.map((c) => ({ valor: String(c.codigoIbge), texto: `${c.nome}/${c.uf}` }))}
          valor={cidade}
          aoMudar={setCidade}
          erro={erros.cidade}
        />
        <CampoTexto
          nome="logradouro"
          rotulo="Rua"
          valor={logradouro}
          aoMudar={setLogradouro}
          autoComplete="address-line1"
          maxLength={120}
          erro={erros.logradouro}
        />
        <div className={estilos.linha}>
          <CampoTexto
            nome="numero"
            rotulo="Número"
            valor={numero}
            aoMudar={setNumero}
            inputMode="numeric"
            maxLength={20}
            erro={erros.numero}
          />
          <CampoTexto nome="bairro" rotulo="Bairro" valor={bairro} aoMudar={setBairro} maxLength={80} erro={erros.bairro} />
        </div>
        <CampoTexto
          nome="complemento"
          rotulo="Complemento"
          valor={complemento}
          aoMudar={setComplemento}
          maxLength={80}
          dica="Apto, bloco, fundos…"
          erro={erros.complemento}
        />
        <p className={estilos.oculto}>
          <Icone nome="eyeoff" tamanho={18} />
          {primeiro} só vê o endereço completo depois do pagamento. Antes disso, só a cidade.
        </p>
      </fieldset>
      {erroGeral && (
        <Nota tipo="erro" anunciar>
          {erroGeral}
        </Nota>
      )}
      <div className={estilos.acoes}>
        <Botao variante="contorno" onClick={() => setEtapa(1)}>
          Mudar os dias
        </Botao>
        <Botao onClick={enviar} desabilitado={enviando}>
          Ver valores e pagar
        </Botao>
      </div>
    </div>
  );
}
