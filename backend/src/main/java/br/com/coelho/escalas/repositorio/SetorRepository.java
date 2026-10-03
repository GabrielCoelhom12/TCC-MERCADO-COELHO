package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Setor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SetorRepository extends JpaRepository<Setor, Long> {
    List<Setor> findAllByOrderByNomeAsc();
}
