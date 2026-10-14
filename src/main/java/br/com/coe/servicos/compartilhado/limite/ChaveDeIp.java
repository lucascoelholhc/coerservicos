package br.com.coe.servicos.compartilhado.limite;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Chave de IP para os baldes (CORE-07). IPv4 como está; IPv6 pelo prefixo /64 (um cliente costuma
 * receber o /64 inteiro e trocar o resto à vontade); IPv4 mapeado em IPv6 vira IPv4. Endereço que não
 * se reconhece vira uma chave fixa: todos os estranhos dividem o mesmo balde (fail-closed).
 */
public final class ChaveDeIp {

    static final String DESCONHECIDO = "desconhecido";
    // Só dígitos, pontos, hexadecimais e dois-pontos: nunca deixa o InetAddress resolver um nome (DNS).
    private static final Pattern FORMATO_DE_IP = Pattern.compile("[0-9a-fA-F:.]{2,45}");
    private static final int GRUPOS_DO_PREFIXO = 4;

    private ChaveDeIp() {}

    /** A chave que reúne os endereços não reconhecidos (não vale como IP para gravar). */
    public static boolean desconhecido(String chave) {
        return DESCONHECIDO.equals(chave);
    }

    public static String de(String ip) {
        if (ip == null || !FORMATO_DE_IP.matcher(ip).matches()) {
            return DESCONHECIDO;
        }
        InetAddress endereco;
        try {
            endereco = InetAddress.getByName(ip);
        } catch (UnknownHostException invalido) {
            return DESCONHECIDO;
        }
        if (endereco instanceof Inet4Address) {
            return endereco.getHostAddress();
        }
        if (endereco instanceof Inet6Address) {
            return prefixo64(endereco.getAddress());
        }
        return DESCONHECIDO;
    }

    private static String prefixo64(byte[] bytes) {
        StringBuilder chave = new StringBuilder();
        boolean todosZero = true;
        for (int grupo = 0; grupo < GRUPOS_DO_PREFIXO; grupo++) {
            int valor = ((bytes[2 * grupo] & 0xff) << 8) | (bytes[2 * grupo + 1] & 0xff);
            todosZero &= valor == 0;
            if (grupo > 0) {
                chave.append(':');
            }
            chave.append(Integer.toHexString(valor));
        }
        return (todosZero ? "" : chave.toString()) + "::/64";
    }
}
