package br.com.coe.servicos.pagamento;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Financeiro no painel do admin (DOM-11 parte 2b): saldos e período, e a tabela por diária. */
@RestController
@RequestMapping("/api/admin/financeiro")
class AdminFinanceiroController {

    private final AdminFinanceiroService servico;

    AdminFinanceiroController(AdminFinanceiroService servico) {
        this.servico = servico;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    AdminFinanceiroService.Resumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return servico.resumo(de, ate);
    }

    @GetMapping("/diarias")
    @PreAuthorize("hasRole('ADMIN')")
    AdminFinanceiroService.Pagina porDiaria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) UUID contrato,
            @RequestParam(defaultValue = "0") int pagina) {
        return servico.porDiaria(de, ate, contrato, pagina);
    }
}
