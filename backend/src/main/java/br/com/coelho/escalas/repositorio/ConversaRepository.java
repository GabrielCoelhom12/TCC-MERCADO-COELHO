package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Conversa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {
    Optional<Conversa> findByChaveDireta(String chaveDireta);
}
