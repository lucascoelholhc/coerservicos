package br.com.coe.servicos.contrato;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Área do profissional (FE-06): Hoje, agenda e Dinheiro. Só o próprio profissional (o perfil vem do token). */
@RestController
@RequestMapping("/api/profissional")
class AreaDoProfissionalController {

    private final AreaDoProfissionalService area;

    AreaDoProfissionalController(AreaDoProfissionalService area) {
        this.area = area;
    }

    @GetMapping("/diarias")
    @PreAuthorize("hasRole('PROFISSIONAL')")
    AreaDoProfissionalDtos.Diarias diarias() {
        return area.diarias();
    }

    @GetMapping("/dinheiro")
    @PreAuthorize("hasRole('PROFISSIONAL')")
    AreaDoProfissionalDtos.Saldo dinheiro() {
        return area.dinheiro();
    }

    @GetMapping("/agenda")
    @PreAuthorize("hasRole('PROFISSIONAL')")
    AreaDoProfissionalDtos.Agenda agenda() {
        return area.agenda();
    }

    @PutMapping("/agenda/folgas/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @LimitadoPorUsuario("agenda-usuario")
    void marcarFolga(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        area.marcarFolga(data);
    }

    @DeleteMapping("/agenda/folgas/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @LimitadoPorUsuario("agenda-usuario")
    void tirarFolga(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        area.tirarFolga(data);
    }
}
