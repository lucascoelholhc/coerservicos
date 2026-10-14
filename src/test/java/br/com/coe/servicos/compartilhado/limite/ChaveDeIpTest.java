package br.com.coe.servicos.compartilhado.limite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CORE-07: chave de IP do balde. IPv6 por /64 (um cliente costuma ter o /64 inteiro). */
class ChaveDeIpTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(textBlock = """
            203.0.113.7,                 203.0.113.7
            2001:db8:1:2:3:4:5:6,        2001:db8:1:2::/64
            2001:db8:1:2:ffff::1,        2001:db8:1:2::/64
            2001:DB8:0001:0002::9,       2001:db8:1:2::/64
            ::ffff:198.51.100.4,         198.51.100.4
            0:0:0:0:0:0:0:1,             ::/64
            """)
    @DisplayName("IPv4 como está; IPv6 pelo prefixo /64; IPv4 mapeado em IPv6 vira IPv4")
    void chave(String ip, String esperado) {
        assertThat(ChaveDeIp.de(ip)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"''", "nao-e-ip", "999.1.1.1"})
    @DisplayName("endereço estranho vira uma chave única e fixa (fail-closed: todos dividem o mesmo balde)")
    void estranho(String ip) {
        assertThat(ChaveDeIp.de(ip)).isEqualTo("desconhecido");
    }
}
