package br.com.coe.servicos.catalogo;

import java.util.List;

/**
 * Catálogo público (DOM-01): áreas com profissões e serviços ativos, e cidades ativas. Só o que o
 * front usa para filtros e categorias: sem ids internos, faixa de diária, NR-10 ou LC 150. O
 * identificador público é o {@code codigo} da profissão e o {@code codigoIbge} da cidade. O front
 * escolhe a ilustração pelo {@code codigo} da profissão, não pelo {@code icone}.
 */
public record CatalogoResposta(List<Area> areas, List<Cidade> cidades) {

    public CatalogoResposta {
        areas = List.copyOf(areas);
        cidades = List.copyOf(cidades);
    }

    public record Area(String codigo, String nome, List<Profissao> profissoes) {
        public Area {
            profissoes = List.copyOf(profissoes);
        }
    }

    public record Profissao(String codigo, String nome, String nomePlural, String icone, List<Servico> servicos) {
        public Profissao {
            servicos = List.copyOf(servicos);
        }
    }

    public record Servico(String nome) {}

    public record Cidade(int codigoIbge, String nome, String uf) {}
}
