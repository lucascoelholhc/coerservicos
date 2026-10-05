package br.com.coe.servicos.usuario;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    boolean existsByCelular(String celular);

    boolean existsByEmail(String email);
}
