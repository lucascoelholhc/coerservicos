package br.com.coe.servicos.usuario;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AceiteTermosRepository extends JpaRepository<AceiteTermos, UUID> {}
