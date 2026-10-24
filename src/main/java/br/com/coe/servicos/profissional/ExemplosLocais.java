package br.com.coe.servicos.profissional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Profissionais de exemplo do perfil local (DB-11), os mesmos do protótipo ({@code docs/prototipo/js/dados.js}).
 * Todos fictícios: CPFs de bases {@code 999000101…} com dígito verificador válido, celulares {@code 479000501NN}, e-mails {@code profissionalNN@coe.local},
 * chaves Pix aleatórias fixas. Ids fixos para o seeder saber o que já criou. Só o {@link SeederLocal} usa.
 */
final class ExemplosLocais {

    /** Serviço do catálogo pela profissão e pelo nome (como o PUT /servicos recebe). */
    record Servico(String profissao, String nome) {}

    /** Foto do portfólio: legenda e serviço (a imagem é gerada, com "EXEMPLO"). */
    record Foto(String legenda, String servico) {}

    record Exemplo(
            UUID usuario,
            UUID profissional,
            String nome,
            String celular,
            String email,
            String cpf,
            LocalDate nascimento,
            String profissao,
            List<String> outras,
            List<Servico> servicos,
            int anos,
            String ferramentas,
            boolean mei,
            String valor,
            String meia,
            String horario,
            int cidade,
            int raio,
            List<Integer> outrasCidades,
            Set<Integer> dias,
            String sobre,
            String pix,
            boolean ativo,
            boolean nr10,
            int cor,
            List<Foto> fotos) {

        Exemplo comNascimento(LocalDate outro) {
            return new Exemplo(
                    usuario,
                    profissional,
                    nome,
                    celular,
                    email,
                    cpf,
                    outro,
                    profissao,
                    outras,
                    servicos,
                    anos,
                    ferramentas,
                    mei,
                    valor,
                    meia,
                    horario,
                    cidade,
                    raio,
                    outrasCidades,
                    dias,
                    sobre,
                    pix,
                    ativo,
                    nr10,
                    cor,
                    fotos);
        }

        Exemplo comRaio(int outro) {
            return new Exemplo(
                    usuario,
                    profissional,
                    nome,
                    celular,
                    email,
                    cpf,
                    nascimento,
                    profissao,
                    outras,
                    servicos,
                    anos,
                    ferramentas,
                    mei,
                    valor,
                    meia,
                    horario,
                    cidade,
                    outro,
                    outrasCidades,
                    dias,
                    sobre,
                    pix,
                    ativo,
                    nr10,
                    cor,
                    fotos);
        }

        /** Nunca CPF, Pix nem celular no log. */
        @Override
        public String toString() {
            return "Exemplo[profissional=" + profissional + "]";
        }
    }

    // códigos IBGE das cidades do lançamento (V11)
    private static final int BLUMENAU = 4202404;
    private static final int GASPAR = 4205902;
    private static final int POMERODE = 4213203;
    private static final int INDAIAL = 4207502;
    private static final int TIMBO = 4218202;
    private static final int BRUSQUE = 4202909;

    private static final Set<Integer> SEGUNDA_A_SABADO = Set.of(1, 2, 3, 4, 5, 6);
    private static final Set<Integer> SEGUNDA_A_SEXTA = Set.of(1, 2, 3, 4, 5);
    private static final Set<Integer> TERCA_QUINTA_SABADO = Set.of(2, 4, 6);

    static final List<Exemplo> TODOS = List.of(
            ativo(
                    1,
                    "Valdir Schmitt",
                    "99900010140",
                    LocalDate.of(1978, 3, 14),
                    "pedreiro",
                    List.of(),
                    servicos("pedreiro", "Alvenaria", "Reboco", "Contrapiso", "Assentamento de piso", "Muro e calçada"),
                    20,
                    "sim",
                    true,
                    "280.00",
                    "160.00",
                    "7h às 17h",
                    BLUMENAU,
                    20,
                    List.of(GASPAR, POMERODE, INDAIAL),
                    SEGUNDA_A_SABADO,
                    "Trabalho com construção há 22 anos. Faço obra do começo ao fim e pequenos reparos também. "
                            + "Levo minhas ferramentas e deixo o local limpo no fim do dia.",
                    false,
                    0x2B5C8A,
                    fotos("Muro de arrimo no Garcia", "Muro e calçada", "Contrapiso nivelado", "Contrapiso")),
            ativo(
                    2,
                    "Anderson Pereira",
                    "99900010221",
                    LocalDate.of(1992, 7, 2),
                    "pedreiro",
                    List.of("pintor"),
                    junto(
                            servicos("pedreiro", "Reboco", "Conserto de trincas", "Muro e calçada", "Pequenos reparos"),
                            servicos("pintor", "Pintura de muro")),
                    5,
                    "parte",
                    false,
                    "260.00",
                    null,
                    "7h às 16h",
                    BLUMENAU,
                    10,
                    List.of(GASPAR),
                    SEGUNDA_A_SABADO,
                    "Faço reboco, conserto de trincas e muros. Pinto o muro depois de pronto. "
                            + "Atendo a região sul da cidade.",
                    false,
                    0x2F7A6B,
                    fotos("Muro lateral", "Muro e calçada", "Trinca consertada", "Conserto de trincas")),
            ativo(
                    3,
                    "Jair Hoffmann",
                    "99900010302",
                    LocalDate.of(1980, 11, 21),
                    "pedreiro",
                    List.of(),
                    servicos("pedreiro", "Telhado e calhas", "Alvenaria", "Muro e calçada", "Pequenos reparos"),
                    10,
                    "sim",
                    true,
                    "320.00",
                    "180.00",
                    "7h às 17h",
                    BLUMENAU,
                    40,
                    List.of(GASPAR, POMERODE, INDAIAL, BRUSQUE),
                    SEGUNDA_A_SEXTA,
                    "Telhados, calhas e alvenaria. Tenho equipamento de segurança para trabalho em altura.",
                    false,
                    0x8A4B2F,
                    fotos("Troca de telhas", "Telhado e calhas", "Parede de bloco", "Alvenaria")),
            ativo(
                    4,
                    "Marcos Lenzi",
                    "99900010493",
                    LocalDate.of(1985, 1, 30),
                    "pintor",
                    List.of(),
                    servicos(
                            "pintor",
                            "Pintura interna",
                            "Pintura externa",
                            "Massa corrida",
                            "Textura e grafiato",
                            "Portas e janelas"),
                    10,
                    "sim",
                    true,
                    "250.00",
                    "140.00",
                    "7h30 às 17h",
                    BLUMENAU,
                    20,
                    List.of(GASPAR, POMERODE),
                    SEGUNDA_A_SABADO,
                    "Pintor há 15 anos. Protejo móveis e piso com lona, faço massa corrida e lixa antes de pintar. "
                            + "Ajudo a escolher a tinta.",
                    false,
                    0x7A3E5C,
                    fotos("Sala em verde-oliva", "Pintura interna", "Fachada de sobrado", "Pintura externa")),
            ativo(
                    5,
                    "Fábio Rosa",
                    "99900010574",
                    LocalDate.of(1996, 9, 8),
                    "pintor",
                    List.of(),
                    servicos("pintor", "Pintura interna", "Pintura de muro", "Massa corrida"),
                    5,
                    "parte",
                    false,
                    "230.00",
                    "130.00",
                    "8h às 17h",
                    GASPAR,
                    20,
                    List.of(BLUMENAU),
                    SEGUNDA_A_SEXTA,
                    "Pinto casas e apartamentos na região norte. Trabalho caprichado, com fita e lona.",
                    false,
                    0x3D5A99,
                    fotos("Apartamento inteiro", "Pintura interna", "Muro da frente", "Pintura de muro")),
            ativo(
                    6,
                    "Cristiano Wagner",
                    "99900010655",
                    LocalDate.of(1988, 4, 17),
                    "eletricista",
                    List.of(),
                    servicos(
                            "eletricista",
                            "Tomadas e interruptores",
                            "Chuveiro e resistência",
                            "Quadro de luz e disjuntor",
                            "Luminárias e ventilador"),
                    10,
                    "sim",
                    true,
                    "300.00",
                    "170.00",
                    "8h às 17h",
                    BLUMENAU,
                    40,
                    List.of(INDAIAL, TIMBO, POMERODE),
                    SEGUNDA_A_SABADO,
                    "Eletricista com curso de NR-10. Troca de quadro, disjuntores, tomadas e chuveiro. "
                            + "Deixo tudo testado antes de ir embora.",
                    true,
                    0x4B5F2A,
                    fotos(
                            "Quadro de luz novo",
                            "Quadro de luz e disjuntor",
                            "Tomadas da cozinha",
                            "Tomadas e interruptores")),
            ativo(
                    7,
                    "Edson Tomio",
                    "99900010736",
                    LocalDate.of(1972, 12, 5),
                    "eletricista",
                    List.of(),
                    servicos(
                            "eletricista",
                            "Fiação nova",
                            "Padrão de entrada",
                            "Quadro de luz e disjuntor",
                            "Tomadas e interruptores"),
                    20,
                    "sim",
                    true,
                    "340.00",
                    "190.00",
                    "7h às 16h",
                    BLUMENAU,
                    20,
                    List.of(GASPAR),
                    SEGUNDA_A_SEXTA,
                    "25 anos de profissão. Faço fiação completa de casa nova, padrão de entrada e reforma de "
                            + "instalação antiga.",
                    true,
                    0x5B4A8A,
                    fotos("Fiação de casa nova", "Fiação nova", "Padrão de entrada", "Padrão de entrada")),
            ativo(
                    8,
                    "Osni Beduschi",
                    "99900010817",
                    LocalDate.of(1966, 6, 26),
                    "jardineiro",
                    List.of(),
                    servicos(
                            "jardineiro",
                            "Corte de grama",
                            "Poda de árvores e cercas",
                            "Limpeza de terreno",
                            "Recolher entulho verde"),
                    20,
                    "sim",
                    false,
                    "200.00",
                    "110.00",
                    "7h às 16h",
                    BLUMENAU,
                    20,
                    List.of(GASPAR),
                    SEGUNDA_A_SABADO,
                    "Cuido de jardins há 30 anos. Corte de grama, poda e limpeza de terreno. "
                            + "Levo o entulho verde embora.",
                    false,
                    0x2E6B3A,
                    fotos("Gramado cortado", "Corte de grama", "Cerca viva podada", "Poda de árvores e cercas")),
            ativo(
                    9,
                    "Gilmar Theiss",
                    "99900010906",
                    LocalDate.of(1998, 2, 11),
                    "jardineiro",
                    List.of(),
                    servicos("jardineiro", "Corte de grama", "Plantio", "Manutenção mensal"),
                    3,
                    "parte",
                    false,
                    "180.00",
                    "100.00",
                    "7h às 16h",
                    POMERODE,
                    20,
                    List.of(BLUMENAU),
                    TERCA_QUINTA_SABADO,
                    "Faço corte de grama, plantio e manutenção mensal de jardim. "
                            + "Atendo terças, quintas e sábados.",
                    false,
                    0x6B7A2E,
                    fotos("Canteiro plantado", "Plantio", "Jardim mantido", "Manutenção mensal")),
            ativo(
                    10,
                    "Rosângela Werner",
                    "99900011031",
                    LocalDate.of(1979, 8, 19),
                    "diarista",
                    List.of(),
                    servicos("diarista", "Limpeza completa", "Passar roupa", "Pós-obra", "Organização"),
                    10,
                    "parte",
                    true,
                    "190.00",
                    "110.00",
                    "8h às 17h",
                    BLUMENAU,
                    10,
                    List.of(),
                    SEGUNDA_A_SEXTA,
                    "Diarista há 15 anos. Limpeza caprichada, passo roupa e faço pós-obra. "
                            + "Levo meus panos; o cliente fornece os produtos.",
                    false,
                    0xB0476A,
                    fotos("Cozinha brilhando", "Limpeza completa", "Roupa passada", "Passar roupa")),
            ativo(
                    11,
                    "Márcia dos Santos",
                    "99900011112",
                    LocalDate.of(1994, 10, 3),
                    "diarista",
                    List.of(),
                    servicos("diarista", "Limpeza completa", "Organização"),
                    3,
                    "nao",
                    false,
                    "170.00",
                    null,
                    "8h às 16h",
                    GASPAR,
                    20,
                    List.of(BLUMENAU),
                    TERCA_QUINTA_SABADO,
                    "Limpeza completa e organização de armários. Atendo terças, quintas e sábados.",
                    false,
                    0x3E6FA8,
                    fotos("Armários organizados", "Organização", "Cozinha", "Limpeza completa")),
            ativo(
                    12,
                    "Ivone Kreutzfeld",
                    "99900011201",
                    LocalDate.of(1983, 5, 27),
                    "diarista",
                    List.of(),
                    servicos("diarista", "Limpeza pesada", "Pós-obra", "Vidros e janelas"),
                    10,
                    "sim",
                    true,
                    "210.00",
                    "120.00",
                    "8h às 17h",
                    BLUMENAU,
                    20,
                    List.of(POMERODE),
                    SEGUNDA_A_SABADO,
                    "Limpeza pesada, pós-obra e vidros de sacada. Experiência com apartamentos e salas comerciais.",
                    false,
                    0x7A5C2E,
                    fotos("Pós-obra", "Pós-obra", "Vidros da sacada", "Vidros e janelas")),
            emAnalise(
                    13,
                    "Cleusa Rodrigues",
                    "99900011384",
                    LocalDate.of(1987, 3, 9),
                    "diarista",
                    servicos("diarista", "Limpeza completa", "Organização"),
                    5,
                    "nao",
                    "175.00",
                    null,
                    "8h às 17h",
                    BLUMENAU,
                    20,
                    List.of(GASPAR),
                    SEGUNDA_A_SEXTA,
                    "Faço limpeza completa e organização. Chego no horário combinado.",
                    false,
                    0x9A4F7C,
                    fotos("Sala arrumada", "Limpeza completa", "Armário organizado", "Organização")),
            emAnalise(
                    14,
                    "Ricardo Deschamps",
                    "99900011465",
                    LocalDate.of(1984, 1, 22),
                    "pedreiro",
                    servicos("pedreiro", "Alvenaria", "Reboco", "Contrapiso"),
                    10,
                    "sim",
                    "290.00",
                    "160.00",
                    "7h às 17h",
                    BLUMENAU,
                    20,
                    List.of(INDAIAL),
                    SEGUNDA_A_SEXTA,
                    "Pedreiro há 12 anos, trabalho com alvenaria e reboco. Tenho betoneira própria.",
                    false,
                    0x6A4A2F,
                    fotos("Parede levantada", "Alvenaria", "Reboco pronto", "Reboco")),
            // no protótipo o "Sobre" dele tinha telefone (para o admin recusar); aqui passa pela censura do
            // cadastro, então o texto é limpo
            emAnalise(
                    15,
                    "Nelson Kraus",
                    "99900011546",
                    LocalDate.of(1990, 7, 15),
                    "eletricista",
                    servicos("eletricista", "Tomadas e interruptores", "Chuveiro e resistência"),
                    5,
                    "sim",
                    "290.00",
                    "160.00",
                    "8h às 17h",
                    BRUSQUE,
                    20,
                    List.of(GASPAR),
                    SEGUNDA_A_SABADO,
                    "Eletricista residencial. Troco tomadas, interruptores e chuveiro com cuidado.",
                    true,
                    0x4A5F7A,
                    fotos("Tomadas novas", "Tomadas e interruptores", "Chuveiro trocado", "Chuveiro e resistência")));

    private ExemplosLocais() {}

    private static Exemplo ativo(
            int n,
            String nome,
            String cpf,
            LocalDate nascimento,
            String profissao,
            List<String> outras,
            List<Servico> servicos,
            int anos,
            String ferramentas,
            boolean mei,
            String valor,
            String meia,
            String horario,
            int cidade,
            int raio,
            List<Integer> outrasCidades,
            Set<Integer> dias,
            String sobre,
            boolean nr10,
            int cor,
            List<Foto> fotos) {
        return new Exemplo(
                usuario(n),
                profissional(n),
                nome,
                celular(n),
                email(n),
                cpf,
                nascimento,
                profissao,
                outras,
                servicos,
                anos,
                ferramentas,
                mei,
                valor,
                meia,
                horario,
                cidade,
                raio,
                outrasCidades,
                dias,
                sobre,
                pix(n),
                true,
                nr10,
                cor,
                fotos);
    }

    private static Exemplo emAnalise(
            int n,
            String nome,
            String cpf,
            LocalDate nascimento,
            String profissao,
            List<Servico> servicos,
            int anos,
            String ferramentas,
            String valor,
            String meia,
            String horario,
            int cidade,
            int raio,
            List<Integer> outrasCidades,
            Set<Integer> dias,
            String sobre,
            boolean nr10,
            int cor,
            List<Foto> fotos) {
        return new Exemplo(
                usuario(n),
                profissional(n),
                nome,
                celular(n),
                email(n),
                cpf,
                nascimento,
                profissao,
                List.of(),
                servicos,
                anos,
                ferramentas,
                false,
                valor,
                meia,
                horario,
                cidade,
                raio,
                outrasCidades,
                dias,
                sobre,
                pix(n),
                false,
                nr10,
                cor,
                fotos);
    }

    static UUID usuario(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-0000000b%04d", n));
    }

    static UUID profissional(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-0000000d%04d", n));
    }

    static String celular(int n) {
        return String.valueOf(47_900_050_100L + n);
    }

    /** E-mail confirmado: sem ele a conta fica com contato pendente (RN61). */
    static String email(int n) {
        return String.format("profissional%02d@coe.local", n);
    }

    private static String pix(int n) {
        return String.format("00000000-0000-4000-8000-%012d", n);
    }

    private static List<Servico> servicos(String profissao, String... nomes) {
        List<Servico> lista = new ArrayList<>();
        for (String nome : nomes) {
            lista.add(new Servico(profissao, nome));
        }
        return List.copyOf(lista);
    }

    private static List<Servico> junto(List<Servico> uma, List<Servico> outra) {
        List<Servico> lista = new ArrayList<>(uma);
        lista.addAll(outra);
        return List.copyOf(lista);
    }

    /** Pares legenda, serviço. */
    private static List<Foto> fotos(String... pares) {
        List<Foto> lista = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            lista.add(new Foto(pares[i], pares[i + 1]));
        }
        return List.copyOf(lista);
    }
}
